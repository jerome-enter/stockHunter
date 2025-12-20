package com.jeromeent.stockhunter.us.service

import com.jeromeent.stockhunter.common.model.*
import com.jeromeent.stockhunter.common.util.*
import com.jeromeent.stockhunter.us.client.KISUSApiClient
import com.jeromeent.stockhunter.us.model.*
import kotlinx.coroutines.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * 미국주식 스크리너
 */
class USStockScreener(
    private val kisApiClient: KISUSApiClient
) {
    
    suspend fun screen(condition: USScreeningCondition): ScreeningResult<USStockData> = coroutineScope {
        val startTime = System.currentTimeMillis()
        
        logger.info { "Starting US stock screening with exchange: ${condition.exchangeCode}" }
        
        // 1. 심볼 목록 가져오기
        val symbols = if (condition.targetCodes.isNotEmpty()) {
            condition.targetCodes
        } else {
            kisApiClient.getAllUSSymbols(condition.exchangeCode)
        }
        
        logger.info { "Screening ${symbols.size} US stocks..." }
        
        // 2. 병렬 처리
        val filteredStocks = symbols
            .chunkedSafe(50)  // 미국주식은 50개씩
            .map { chunk ->
                async(Dispatchers.IO) {
                    chunk.mapNotNull { symbol ->
                        try {
                            fetchAndFilter(symbol, condition)
                        } catch (e: Exception) {
                            logger.warn { "Failed to process $symbol: ${e.message}" }
                            null
                        }
                    }
                }
            }
            .awaitAll()
            .flatten()
        
        val executionTime = System.currentTimeMillis() - startTime
        
        logger.info { 
            "US screening completed: ${filteredStocks.size}/${symbols.size} stocks matched in ${executionTime}ms" 
        }
        
        ScreeningResult(
            stocks = filteredStocks,
            totalScanned = symbols.size,
            matchedCount = filteredStocks.size,
            executionTimeMs = executionTime,
            timestamp = now(),
            market = "US_${condition.exchangeCode}"
        )
    }
    
    private suspend fun fetchAndFilter(
        symbol: String,
        condition: USScreeningCondition
    ): USStockData? {
        // 1. 일별 시세 조회
        val priceResponse = kisApiClient.getDailyPrice(symbol, condition.exchangeCode, days = 250)
        
        if (priceResponse.output1.isEmpty()) {
            logger.debug { "No data for $symbol" }
            return null
        }
        
        // 2. ETF 필터링 (미국 ETF는 보통 QQQ, SPY 등으로 끝남)
        if (condition.excludeETF) {
            val etfPatterns = listOf("QQQ", "SPY", "DIA", "IWM", "EEM", "GLD", "SLV")
            if (etfPatterns.any { symbol.contains(it, ignoreCase = true) }) {
                return null
            }
        }
        
        // 3. 데이터 변환
        val priceData = priceResponse.output1
        val prices = priceData.map { it.clos.toDoubleOrDefault() }
        val volumes = priceData.map { it.tvol.toLongOrDefault() }
        
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
        if (condition.ma60Enabled && ma60 != null) {
            val ratio = currentPrice.toPercentage(ma60)
            if (ratio !in condition.ma60Min..condition.ma60Max) return null
        }
        
        if (condition.ma112Enabled && ma112 != null) {
            val ratio = currentPrice.toPercentage(ma112)
            if (ratio !in condition.ma112Min..condition.ma112Max) return null
        }
        
        if (condition.ma224Enabled && ma224 != null) {
            val ratio = currentPrice.toPercentage(ma224)
            if (ratio !in condition.ma224Min..condition.ma224Max) return null
        }
        
        // 6. 이평선 정배열 체크
        if (condition.maAlignment) {
            if (!TechnicalIndicators.isMAAligned(ma5, ma20, ma60, ma112)) {
                return null
            }
        }
        
        // 7. 볼린저 밴드
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
                
                if (condition.bbUpperBreak && currentPrice < bb.upper) return null
                if (condition.bbLowerBreak && currentPrice > bb.lower) return null
            }
        }
        
        // 8. 거래량 필터링
        if (condition.volumeEnabled) {
            val avgVolume = TechnicalIndicators.calculateAverageVolume(volumes, 20)
            if (avgVolume != null) {
                val volumeRatio = currentVolume.toDouble() / avgVolume
                if (volumeRatio < condition.volumeMultiple) return null
            }
        }
        
        // 9. 가격 변동 필터링
        val changePercent = ((currentPrice - prevPrice) / prevPrice) * 100.0
        if (condition.priceChangeEnabled) {
            if (changePercent !in condition.priceChangeMin..condition.priceChangeMax) {
                return null
            }
        }
        
        // 10. 현재가 정보 조회 (PER, PBR 등)
        val basicInfo = if (condition.marketCapEnabled || condition.perEnabled || condition.pbrEnabled) {
            kisApiClient.getCurrentPrice(symbol, condition.exchangeCode)?.output
        } else null
        
        val marketCap = basicInfo?.t_xprc?.toLongOrDefault()
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
        
        // 11. 결과 생성
        val avgVolume = TechnicalIndicators.calculateAverageVolume(volumes, 20)
        val volumeRatio = if (avgVolume != null && avgVolume > 0) {
            (currentVolume.toDouble() / avgVolume).roundTo(2)
        } else null
        
        return USStockData(
            symbol = symbol,
            name = symbol,  // 종목명은 별도 API 필요 (일단 심볼 사용)
            currentPrice = currentPrice.roundTo(2),
            changePercent = changePercent.roundTo(2),
            volume = currentVolume,
            ma5 = ma5?.roundTo(2),
            ma20 = ma20?.roundTo(2),
            ma60 = ma60?.roundTo(2),
            ma112 = ma112?.roundTo(2),
            ma224 = ma224?.roundTo(2),
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
            exchange = condition.exchangeCode,
            currency = "USD"
        )
    }
}
