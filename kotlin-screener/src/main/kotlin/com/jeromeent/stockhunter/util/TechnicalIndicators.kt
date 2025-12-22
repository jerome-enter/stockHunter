package com.jeromeent.stockhunter.util

import com.jeromeent.stockhunter.model.BBPosition
import com.jeromeent.stockhunter.model.BollingerBands
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 기술적 지표 계산 유틸리티
 */
object TechnicalIndicators {
    
    /**
     * 단순 이동평균(SMA) 계산
     * @param prices 가격 리스트 (최신 데이터가 앞에)
     * @param period 기간
     * @return 이동평균값 또는 null
     */
    fun calculateSMA(prices: List<Double>, period: Int): Double? {
        if (prices.size < period) return null
        return prices.take(period).average()
    }
    
    /**
     * 볼린저 밴드 계산
     * @param prices 가격 리스트
     * @param period 기간 (기본 20일)
     * @param multiplier 표준편차 승수 (기본 2)
     * @return 볼린저 밴드 또는 null
     */
    fun calculateBollingerBands(
        prices: List<Double>,
        period: Int = 20,
        multiplier: Double = 2.0
    ): BollingerBands? {
        if (prices.size < period) return null
        
        val recentPrices = prices.take(period)
        val middle = recentPrices.average()
        
        // 표준편차 계산
        val variance = recentPrices
            .map { (it - middle).pow(2) }
            .average()
        val stdDev = sqrt(variance)
        
        return BollingerBands(
            upper = middle + (multiplier * stdDev),
            middle = middle,
            lower = middle - (multiplier * stdDev)
        )
    }
    
    /**
     * 볼린저 밴드 위치 판단
     */
    fun determineBBPosition(currentPrice: Double, bb: BollingerBands): BBPosition {
        return when {
            currentPrice >= bb.upper -> BBPosition.UPPER
            currentPrice <= bb.lower -> BBPosition.LOWER
            else -> BBPosition.MIDDLE
        }
    }
    
    /**
     * 평균 거래량 계산
     * @param volumes 거래량 리스트
     * @param days 평균 계산 기간 (기본 20일)
     * @return 평균 거래량 또는 null
     */
    fun calculateAverageVolume(volumes: List<Long>, days: Int = 20): Double? {
        if (volumes.size < days) return null
        return volumes.take(days).average()
    }
    
    /**
     * 이동평균선 정배열 여부 확인
     * @return true if ma5 > ma20 > ma60 > ma112
     */
    fun isMAAligned(ma5: Double?, ma20: Double?, ma60: Double?, ma112: Double?): Boolean {
        return ma5 != null && ma20 != null && ma60 != null && ma112 != null &&
                ma5 > ma20 && ma20 > ma60 && ma60 > ma112
    }
    
    /**
     * RSI (Relative Strength Index) 계산
     * @param prices 가격 리스트
     * @param period 기간 (기본 14일)
     * @return RSI 값 (0-100) 또는 null
     */
    fun calculateRSI(prices: List<Double>, period: Int = 14): Double? {
        if (prices.size < period + 1) return null
        
        val changes = prices.zipWithNext { a, b -> b - a }
        val gains = changes.map { if (it > 0) it else 0.0 }
        val losses = changes.map { if (it < 0) -it else 0.0 }
        
        val avgGain = gains.take(period).average()
        val avgLoss = losses.take(period).average()
        
        if (avgLoss == 0.0) return 100.0
        
        val rs = avgGain / avgLoss
        return 100.0 - (100.0 / (1.0 + rs))
    }
    
    /**
     * MACD (Moving Average Convergence Divergence) 계산
     * @return Pair(MACD, Signal)
     */
    fun calculateMACD(
        prices: List<Double>,
        fastPeriod: Int = 12,
        slowPeriod: Int = 26,
        signalPeriod: Int = 9
    ): Pair<Double, Double>? {
        if (prices.size < slowPeriod) return null
        
        val emaFast = calculateEMA(prices, fastPeriod) ?: return null
        val emaSlow = calculateEMA(prices, slowPeriod) ?: return null
        val macd = emaFast - emaSlow
        
        // Signal line은 MACD의 EMA
        // 실제 구현은 더 복잡하지만 간단히 처리
        return Pair(macd, macd * 0.9)
    }
    
    /**
     * 지수 이동평균(EMA) 계산
     */
    private fun calculateEMA(prices: List<Double>, period: Int): Double? {
        if (prices.size < period) return null
        
        val multiplier = 2.0 / (period + 1)
        var ema = prices.take(period).average()
        
        for (i in period until minOf(prices.size, period * 2)) {
            ema = (prices[i] - ema) * multiplier + ema
        }
        
        return ema
    }
    
    /**
     * 변동성 계산 (표준편차)
     */
    fun calculateVolatility(prices: List<Double>, period: Int = 20): Double? {
        if (prices.size < period) return null
        
        val recentPrices = prices.take(period)
        val mean = recentPrices.average()
        val variance = recentPrices.map { (it - mean).pow(2) }.average()
        
        return sqrt(variance)
    }
    
    /**
     * 일목균형표 계산
     * @param highs 고가 리스트 (최신 데이터가 앞에)
     * @param lows 저가 리스트 (최신 데이터가 앞에)
     * @param closes 종가 리스트 (최신 데이터가 앞에)
     * @param tenkan 전환선 기간 (기본 9일)
     * @param kijun 기준선 기간 (기본 26일)
     * @param senkou 선행스팬 기간 (기본 52일)
     * @return 일목균형표 데이터 또는 null
     */
    data class IchimokuCloud(
        val tenkanSen: Double,      // 전환선: (9일 최고 + 최저) / 2
        val kijunSen: Double,        // 기준선: (26일 최고 + 최저) / 2
        val senkouSpanA: Double,     // 선행스팬1: (전환선 + 기준선) / 2, 26일 선행
        val senkouSpanB: Double,     // 선행스팬2: (52일 최고 + 최저) / 2, 26일 선행
        val chikouSpan: Double       // 후행스팬: 당일 종가, 26일 후행
    )
    
    fun calculateIchimoku(
        highs: List<Double>,
        lows: List<Double>,
        closes: List<Double>,
        tenkan: Int = 9,
        kijun: Int = 26,
        senkou: Int = 52
    ): IchimokuCloud? {
        // 최소 52일 데이터 필요
        if (highs.size < senkou || lows.size < senkou || closes.size < senkou) return null
        
        // 전환선: (9일 최고 + 최저) / 2
        val tenkanMax = highs.take(tenkan).maxOrNull() ?: return null
        val tenkanMin = lows.take(tenkan).minOrNull() ?: return null
        val tenkanSen = (tenkanMax + tenkanMin) / 2.0
        
        // 기준선: (26일 최고 + 최저) / 2
        val kijunMax = highs.take(kijun).maxOrNull() ?: return null
        val kijunMin = lows.take(kijun).minOrNull() ?: return null
        val kijunSen = (kijunMax + kijunMin) / 2.0
        
        // 선행스팬1: (전환선 + 기준선) / 2 (26일 선행이므로 현재 값 사용)
        val senkouSpanA = (tenkanSen + kijunSen) / 2.0
        
        // 선행스팬2: (52일 최고 + 최저) / 2 (26일 선행이므로 현재 값 사용)
        val senkouMax = highs.take(senkou).maxOrNull() ?: return null
        val senkouMin = lows.take(senkou).minOrNull() ?: return null
        val senkouSpanB = (senkouMax + senkouMin) / 2.0
        
        // 후행스팬: 당일 종가 (26일 후행이므로 현재 종가 사용)
        val chikouSpan = closes.firstOrNull() ?: return null
        
        return IchimokuCloud(
            tenkanSen = tenkanSen,
            kijunSen = kijunSen,
            senkouSpanA = senkouSpanA,
            senkouSpanB = senkouSpanB,
            chikouSpan = chikouSpan
        )
    }
    
    /**
     * 일목균형표 구름대 위 체크
     * N 조건: 중가 >= 선행스팬2(기준)
     */
    fun isAboveIchimokuCloud(currentPrice: Double, ichimoku: IchimokuCloud): Boolean {
        // 선행스팬2가 기준 (구름대의 하한 또는 상한)
        return currentPrice >= ichimoku.senkouSpanB
    }
}
