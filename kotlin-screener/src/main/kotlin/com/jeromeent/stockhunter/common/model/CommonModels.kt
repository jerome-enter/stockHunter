package com.jeromeent.stockhunter.common.model

import kotlinx.serialization.Serializable

/**
 * 공통 주식 데이터 인터페이스
 */
interface StockData {
    val identifier: String  // 종목코드 또는 심볼
    val name: String
    val currentPrice: Double
    val changePercent: Double
    val volume: Long
    val ma5: Double?
    val ma20: Double?
    val ma60: Double?
    val ma112: Double?
    val ma224: Double?
    val ma60Ratio: Double?
    val ma112Ratio: Double?
    val ma224Ratio: Double?
    val volumeRatio: Double?
    val maAlignment: Boolean
    val marketCap: Long?
    val per: Double?
    val pbr: Double?
}

/**
 * 볼린저 밴드
 */
@Serializable
data class BollingerBands(
    val upper: Double,
    val middle: Double,
    val lower: Double
)

/**
 * 볼린저 밴드 위치
 */
@Serializable
enum class BBPosition {
    UPPER,   // 상단
    MIDDLE,  // 중간
    LOWER    // 하단
}

/**
 * 시장 구분
 */
@Serializable
enum class Market {
    DOMESTIC,   // 국내 (KOSPI/KOSDAQ)
    US_NASDAQ,  // 미국 나스닥
    US_NYSE,    // 미국 뉴욕증권거래소
    US_AMEX     // 미국 아멕스
}

/**
 * 일별 가격 데이터
 */
@Serializable
data class DailyPrice(
    val date: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long
)

/**
 * 스크리닝 결과 (제네릭)
 */
@Serializable
data class ScreeningResult<T>(
    val stocks: List<T>,
    val totalScanned: Int,
    val matchedCount: Int,
    val executionTimeMs: Long,
    val timestamp: String,
    val market: String
)

/**
 * 공통 스크리닝 조건
 */
interface ScreeningCondition {
    val appKey: String
    val appSecret: String
    val market: Market
    
    // 이동평균선
    val ma60Enabled: Boolean
    val ma60Min: Int
    val ma60Max: Int
    val ma112Enabled: Boolean
    val ma112Min: Int
    val ma112Max: Int
    val ma224Enabled: Boolean
    val ma224Min: Int
    val ma224Max: Int
    
    // 볼린저 밴드
    val bbEnabled: Boolean
    val bbPeriod: Int
    val bbMultiplier: Double
    val bbPosition: String
    val bbUpperBreak: Boolean
    val bbLowerBreak: Boolean
    
    // 거래량
    val volumeEnabled: Boolean
    val volumeMultiple: Double
    
    // 가격 변동
    val priceChangeEnabled: Boolean
    val priceChangeMin: Double
    val priceChangeMax: Double
    
    // 재무 비율
    val marketCapEnabled: Boolean
    val marketCapMin: Long
    val marketCapMax: Long
    val perEnabled: Boolean
    val perMin: Double
    val perMax: Double
    val pbrEnabled: Boolean
    val pbrMin: Double
    val pbrMax: Double
    
    // 기타
    val maAlignment: Boolean
    val targetCodes: List<String>
}
