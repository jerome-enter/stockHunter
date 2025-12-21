package com.jeromeent.stockhunter.service

import com.jeromeent.stockhunter.client.KISApiClient
import com.jeromeent.stockhunter.db.PriceDatabase
import com.jeromeent.stockhunter.model.*
import com.jeromeent.stockhunter.util.*
import kotlinx.coroutines.*
import mu.KotlinLogging
import java.time.LocalDate
import com.jeromeent.stockhunter.db.DailyPrice as DBDailyPrice

private val logger = KotlinLogging.logger {}

/**
 * DB 기반 주식 스크리닝 서비스
 * 
 * 기존 StockScreener와의 차이:
 * - API 30일 데이터 대신 DB 280일 데이터 사용
 * - ma60, ma112, ma224 정확하게 계산 가능
 * - 빠른 스크리닝 (API 호출 최소화)
 */
class DBStockScreener(
    private val database: PriceDatabase,
    private val kisApiClient: KISApiClient
) {
    
    /**
     * 스크리닝 실행
     */
    suspend fun screen(condition: ScreeningCondition): ScreeningResult = coroutineScope {
        val startTime = System.currentTimeMillis()
        
        logger.info { "Starting DB-based stock screening..." }
        
        // 1. DB에서 전체 종목 코드 가져오기
        val stockCodes = database.getAllStockCodes()
        logger.info { "Screening ${stockCodes.size} stocks from DB..." }
        
        // 2. 병렬로 스크리닝 (100개씩 청크)
        val filteredStocks = stockCodes
            .chunked(100)
            .map { chunk ->
                async(Dispatchers.IO) {
                    chunk.mapNotNull { code ->
                        try {
                            fetchAndFilter(code, condition)
                        } catch (e: Exception) {
                            logger.warn { "Failed to process $code: ${e.message}" }
                            null
                        }
                    }
                }
            }
            .awaitAll()
            .flatten()
        
        val elapsedTimeMs = System.currentTimeMillis() - startTime
        
        logger.info { "Screening completed: ${filteredStocks.size} matches in ${elapsedTimeMs}ms" }
        
        return@coroutineScope ScreeningResult(
            stocks = filteredStocks,
            totalScanned = stockCodes.size,
            matchedCount = filteredStocks.size,
            executionTimeMs = elapsedTimeMs,
            timestamp = now()
        )
    }
    
    /**
     * 개별 종목 스크리닝
     */
    private suspend fun fetchAndFilter(
        code: String,
        condition: ScreeningCondition
    ): StockData? {
        // 1. DB에서 280일 가격 데이터 가져오기
        val priceData = database.getPrices(code, days = 280)
        
        if (priceData.isEmpty()) {
            logger.debug { "[$code] No data in DB" }
            return null
        }
        
        // 2. 종목명 가져오기 (DB 또는 API)
        val stockName = try {
            kisApiClient.getStockName(code)
        } catch (e: Exception) {
            code  // 실패 시 종목코드 사용
        }
        
        // ETF/ETN 필터링
        if (condition.excludeETF && stockName.contains("ETF")) return null
        if (condition.excludeETN && stockName.contains("ETN")) return null
        
        // 관리종목 필터링
        if (condition.excludeManagement) {
            if (stockName.contains("관리") || stockName.contains("경고")) return null
        }
        
        // 3. 가격 및 거래량 데이터
        val prices = priceData.map { it.close }
        val volumes = priceData.map { it.volume }
        
        val currentPrice = prices.firstOrNull() ?: return null
        val prevPrice = prices.getOrNull(1) ?: currentPrice
        val currentVolume = volumes.firstOrNull() ?: return null
        
        // 4. 기술적 지표 계산
        val ma5 = TechnicalIndicators.calculateSMA(prices, 5)
        val ma20 = TechnicalIndicators.calculateSMA(prices, 20)
        val ma60 = TechnicalIndicators.calculateSMA(prices, 60)
        val ma112 = TechnicalIndicators.calculateSMA(prices, 112)
        val ma224 = TechnicalIndicators.calculateSMA(prices, 224)
        
        // 5. 이동평균선 필터링
        if (condition.ma60Enabled) {
            if (ma60 == null) {
                logger.debug { "[$code] Excluded: insufficient data for ma60" }
                return null
            }
            val ratio = currentPrice.toPercentage(ma60)
            if (ratio !in condition.ma60Min.toDouble()..condition.ma60Max.toDouble()) {
                logger.debug { "[$code] Excluded: ma60 ratio $ratio not in ${condition.ma60Min}~${condition.ma60Max}" }
                return null
            }
        }
        
        if (condition.ma112Enabled) {
            if (ma112 == null) {
                logger.debug { "[$code] Excluded: insufficient data for ma112" }
                return null
            }
            val ratio = currentPrice.toPercentage(ma112)
            if (ratio !in condition.ma112Min.toDouble()..condition.ma112Max.toDouble()) {
                logger.debug { "[$code] Excluded: ma112 ratio $ratio not in ${condition.ma112Min}~${condition.ma112Max}" }
                return null
            }
        }
        
        if (condition.ma224Enabled) {
            if (ma224 == null) {
                logger.debug { "[$code] Excluded: insufficient data for ma224" }
                return null
            }
            val ratio = currentPrice.toPercentage(ma224)
            if (ratio !in condition.ma224Min.toDouble()..condition.ma224Max.toDouble()) {
                logger.debug { "[$code] Excluded: ma224 ratio $ratio not in ${condition.ma224Min}~${condition.ma224Max}" }
                return null
            }
        }
        
        // 6. 이평선 정배열 체크
        if (condition.maAlignment) {
            if (!TechnicalIndicators.isMAAligned(ma5, ma20, ma60, ma112)) {
                logger.debug { "[$code] Excluded: MA not aligned" }
                return null
            }
        }
        
        // 7. 볼린저 밴드 계산 및 필터링
        var bb: BollingerBands? = null
        var bbPosition = BBPosition.MIDDLE
        
        if (condition.bbEnabled) {
            bb = TechnicalIndicators.calculateBollingerBands(
                prices, 
                condition.bbPeriod, 
                condition.bbMultiplier
            )
            
            if (bb != null) {
                bbPosition = TechnicalIndicators.determineBBPosition(currentPrice, bb)
                
                // BB 위치 필터링
                if (condition.bbPosition != "all") {
                    val targetPosition = when (condition.bbPosition.lowercase()) {
                        "upper" -> BBPosition.UPPER
                        "lower" -> BBPosition.LOWER
                        "middle" -> BBPosition.MIDDLE
                        else -> null
                    }
                    
                    if (targetPosition != null && bbPosition != targetPosition) {
                        return null
                    }
                }
                
                // BB 돌파/터치 필터링
                if (condition.bbUpperBreak && currentPrice < bb.upper) return null
                if (condition.bbLowerBreak && currentPrice > bb.lower) return null
            }
        }
        
        // 8. 거래량 필터링
        if (condition.volumeEnabled) {
            val avgVolume = TechnicalIndicators.calculateAverageVolume(volumes, 20)
            if (avgVolume != null) {
                val volumeRatio = currentVolume.toDouble() / avgVolume
                if (volumeRatio < condition.volumeMultiple) {
                    logger.debug { "[$code] Excluded: volume ratio $volumeRatio < ${condition.volumeMultiple}" }
                    return null
                }
            }
        }
        
        // 9. 가격 변동 필터링
        val changePercent = ((currentPrice - prevPrice) / prevPrice) * 100.0
        if (condition.priceChangeEnabled) {
            if (changePercent !in condition.priceChangeMin..condition.priceChangeMax) {
                logger.debug { "[$code] Excluded: change $changePercent not in ${condition.priceChangeMin}~${condition.priceChangeMax}" }
                return null
            }
        }
        
        // 10. 시가총액/재무 비율 필터링 (선택적으로 API 호출)
        var marketCap: Long? = null
        var per: Double? = null
        var pbr: Double? = null
        
        if (condition.marketCapEnabled || condition.perEnabled || condition.pbrEnabled) {
            try {
                val basicInfo = kisApiClient.getCurrentPriceWithInfo(code)?.output
                marketCap = basicInfo?.hts_avls?.toLongOrDefault()
                per = basicInfo?.per?.toDoubleOrDefault()
                pbr = basicInfo?.pbr?.toDoubleOrDefault()
                
                if (condition.marketCapEnabled && marketCap != null) {
                    if (marketCap !in condition.marketCapMin..condition.marketCapMax) {
                        return null
                    }
                }
                
                if (condition.perEnabled && per != null) {
                    if (per < condition.perMin || per > condition.perMax) {
                        return null
                    }
                }
                
                if (condition.pbrEnabled && pbr != null) {
                    if (pbr < condition.pbrMin || pbr > condition.pbrMax) {
                        return null
                    }
                }
            } catch (e: Exception) {
                logger.warn { "[$code] Failed to fetch basic info: ${e.message}" }
            }
        }
        
        // 11. 결과 데이터 생성
        val avgVolume = TechnicalIndicators.calculateAverageVolume(volumes, 20)
        val volumeRatio = if (avgVolume != null && avgVolume > 0) {
            (currentVolume.toDouble() / avgVolume).roundTo(2)
        } else null
        
        return StockData(
            code = code,
            name = stockName,
            currentPrice = currentPrice.roundTo(0),
            changePercent = changePercent.roundTo(2),
            volume = currentVolume,
            ma5 = ma5?.roundTo(0),
            ma20 = ma20?.roundTo(0),
            ma60 = ma60?.roundTo(0),
            ma112 = ma112?.roundTo(0),
            ma224 = ma224?.roundTo(0),
            ma60Ratio = ma60?.let { currentPrice.toPercentage(it).roundTo(2) },
            ma112Ratio = ma112?.let { currentPrice.toPercentage(it).roundTo(2) },
            ma224Ratio = ma224?.let { currentPrice.toPercentage(it).roundTo(2) },
            bollingerBands = bb,
            bbPosition = bbPosition,
            volumeRatio = volumeRatio,
            maAlignment = TechnicalIndicators.isMAAligned(ma5, ma20, ma60, ma112),
            marketCap = marketCap,
            per = per,
            pbr = pbr,
            roe = null,
            foreignRatio = null,
            status = StockStatus.NORMAL
        )
    }
}

// 현재 시간 (ISO 8601 형식)
private fun now(): String = LocalDate.now().toString()
