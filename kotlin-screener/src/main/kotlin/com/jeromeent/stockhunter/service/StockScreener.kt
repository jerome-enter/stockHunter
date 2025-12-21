package com.jeromeent.stockhunter.service

import com.jeromeent.stockhunter.client.KISApiClient
import com.jeromeent.stockhunter.model.*
import com.jeromeent.stockhunter.util.*
import kotlinx.coroutines.*
import mu.KotlinLogging
import kotlin.system.measureTimeMillis

private val logger = KotlinLogging.logger {}

/**
 * 주식 스크리닝 서비스
 * 
 * 주요 기능:
 * - 병렬 데이터 수집 (코루틴)
 * - 기술적 지표 계산
 * - 조건 기반 필터링
 */
class StockScreener(
    private val kisApiClient: KISApiClient
) {
    
    /**
     * 스크리닝 실행 (메인 함수)
     */
    suspend fun screen(condition: ScreeningCondition): ScreeningResult = coroutineScope {
        val startTime = System.currentTimeMillis()
        
        logger.info { "Starting stock screening with condition: $condition" }
        
        // 1. 종목 코드 가져오기
        val stockCodes = if (condition.targetCodes.isNotEmpty()) {
            condition.targetCodes
        } else {
            kisApiClient.getAllStockCodes()
        }
        
        logger.info { "Screening ${stockCodes.size} stocks..." }
        
        // 2. 병렬로 데이터 수집 및 필터링 (100개씩 청크로 분할)
        val filteredStocks = stockCodes
            .chunkedSafe(100)
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
        
        val executionTime = System.currentTimeMillis() - startTime
        
        logger.info { 
            "Screening completed: ${filteredStocks.size}/${stockCodes.size} stocks matched in ${executionTime}ms" 
        }
        
        ScreeningResult(
            stocks = filteredStocks,
            totalScanned = stockCodes.size,
            matchedCount = filteredStocks.size,
            executionTimeMs = executionTime,
            timestamp = now()
        )
    }
    
    /**
     * 개별 종목 데이터 수집 및 필터링
     */
    private suspend fun fetchAndFilter(
        code: String,
        condition: ScreeningCondition
    ): StockData? {
        // 1. API에서 일별 시세 데이터 조회
        // 주의: 한국투자증권 API는 최대 30개만 반환 (API 제한)
        val priceResponse = kisApiClient.getDailyPrice(code, days = 30)
        
        if (priceResponse.output.isEmpty()) {
            logger.debug { "No data for $code" }
            return null
        }
        
        // 2. 기본정보 조회 (시가총액, PER, PBR 등)
        val basicInfo = if (condition.marketCapEnabled || condition.perEnabled || condition.pbrEnabled) {
            kisApiClient.getCurrentPriceWithInfo(code)?.output
        } else null
        
        // 3. 데이터 변환
        val priceData = priceResponse.output
        val apiStockName = priceData.firstOrNull()?.hts_kor_isnm
        val stockName = if (!apiStockName.isNullOrBlank()) {
            apiStockName
        } else {
            kisApiClient.getStockName(code)
        }
        
        // ETF/ETN 필터링
        if (condition.excludeETF && stockName.contains("ETF")) return null
        if (condition.excludeETN && stockName.contains("ETN")) return null
        
        // 관리종목 필터링
        if (condition.excludeManagement) {
            // 종목명에 '관리' 또는 '투자경고' 포함 여부로 간단히 체크
            if (stockName.contains("관리") || stockName.contains("경고")) return null
        }
        
        val prices = priceData.map { it.stck_clpr.toDoubleOrDefault() }
        val volumes = priceData.map { it.acml_vol.toLongOrDefault() }
        
        val currentPrice = prices.firstOrNull() ?: return null
        val prevPrice = prices.getOrNull(1) ?: currentPrice
        val currentVolume = volumes.firstOrNull() ?: return null
        
        // 3. 기술적 지표 계산
        val ma5 = TechnicalIndicators.calculateSMA(prices, 5)
        val ma20 = TechnicalIndicators.calculateSMA(prices, 20)
        val ma60 = TechnicalIndicators.calculateSMA(prices, 60)
        val ma112 = TechnicalIndicators.calculateSMA(prices, 112)
        val ma224 = TechnicalIndicators.calculateSMA(prices, 224)
        
        // ⚠️ API 제한으로 30개 데이터만 받음 - ma60, ma112, ma224는 null됨
        // 대신 ma20을 사용하거나 조건 비활성화
        
        // 4. 이동평균선 필터링
        if (condition.ma60Enabled || condition.ma112Enabled || condition.ma224Enabled) {
            // 30개 데이터로는 ma60, ma112, ma224 계산 불가
            // 대신 ma20을 사용
            if (ma20 == null) {
                logger.debug { "[$code] Excluded: ma20 is null" }
                return null
            }
            val ratio = currentPrice.toPercentage(ma20)
            
            // ma112 조건을 ma20으로 대체
            val targetMin = if (condition.ma112Enabled) condition.ma112Min 
                          else if (condition.ma60Enabled) condition.ma60Min
                          else condition.ma224Min
            val targetMax = if (condition.ma112Enabled) condition.ma112Max
                          else if (condition.ma60Enabled) condition.ma60Max
                          else condition.ma224Max
            
            logger.debug { "[$code/$stockName] ma20=$ma20, currentPrice=$currentPrice, ratio=$ratio, range=$targetMin~$targetMax (ma20 used instead of ma60/ma112/ma224)" }
            
            if (ratio !in targetMin.toDouble()..targetMax.toDouble()) {
                logger.debug { "[$code] Excluded: ma20 ratio $ratio not in $targetMin~$targetMax" }
                return null
            }
        }
        
        if (condition.ma224Enabled) {
            if (ma224 == null) return null  // 데이터 부족 시 제외
            val ratio = currentPrice.toPercentage(ma224)
            if (ratio !in condition.ma224Min.toDouble()..condition.ma224Max.toDouble()) return null
        }
        
        // 5. 이평선 정배열 체크
        if (condition.maAlignment) {
            if (!TechnicalIndicators.isMAAligned(ma5, ma20, ma60, ma112)) {
                return null
            }
        }
        
        // 6. 볼린저 밴드 계산 및 필터링
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
        
        // 7. 거래량 필터링
        if (condition.volumeEnabled) {
            val avgVolume = TechnicalIndicators.calculateAverageVolume(volumes, 20)
            if (avgVolume != null) {
                val volumeRatio = currentVolume.toDouble() / avgVolume
                if (volumeRatio < condition.volumeMultiple) return null
            }
        }
        
        // 8. 가격 변동 필터링
        val changePercent = ((currentPrice - prevPrice) / prevPrice) * 100.0
        if (condition.priceChangeEnabled) {
            if (changePercent !in condition.priceChangeMin..condition.priceChangeMax) {
                return null
            }
        }
        
        // 9. 시가총액/재무 비율 필터링
        val marketCap = basicInfo?.hts_avls?.toLongOrDefault()
        val per = basicInfo?.per?.toDoubleOrDefault()
        val pbr = basicInfo?.pbr?.toDoubleOrDefault()
        
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
        
        // 10. 결과 데이터 생성
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
            pbr = pbr
        )
    }
    
    /**
     * 스트리밍 방식 스크리닝 (각 종목 결과를 즉시 반환)
     */
    fun screenStreaming(
        condition: ScreeningCondition,
        onResult: (StockData) -> Unit,
        onProgress: (Int, Int) -> Unit
    ) = CoroutineScope(Dispatchers.IO).launch {
        val stockCodes = if (condition.targetCodes.isNotEmpty()) {
            condition.targetCodes
        } else {
            kisApiClient.getAllStockCodes()
        }
        
        logger.info { "Starting streaming screening for ${stockCodes.size} stocks" }
        
        var processed = 0
        
        stockCodes.forEach { code ->
            try {
                val result = fetchAndFilter(code, condition)
                if (result != null) {
                    onResult(result)
                }
            } catch (e: Exception) {
                logger.warn { "Failed to process $code: ${e.message}" }
            } finally {
                processed++
                onProgress(processed, stockCodes.size)
            }
        }
        
        logger.info { "Streaming screening completed" }
    }
}
