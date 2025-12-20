package com.jeromeent.stockhunter.us.model

import com.jeromeent.stockhunter.common.model.*
import kotlinx.serialization.Serializable

/**
 * 미국주식 데이터
 */
@Serializable
data class USStockData(
    val symbol: String,  // 티커 심볼 (AAPL, TSLA 등)
    override val name: String,
    override val currentPrice: Double,
    override val changePercent: Double,
    override val volume: Long,
    override val ma5: Double? = null,
    override val ma20: Double? = null,
    override val ma60: Double? = null,
    override val ma112: Double? = null,
    override val ma224: Double? = null,
    override val ma60Ratio: Double? = null,
    override val ma112Ratio: Double? = null,
    override val ma224Ratio: Double? = null,
    val bollingerBands: BollingerBands? = null,
    val bbPosition: BBPosition = BBPosition.MIDDLE,
    override val volumeRatio: Double? = null,
    override val maAlignment: Boolean = false,
    override val marketCap: Long? = null,
    override val per: Double? = null,
    override val pbr: Double? = null,
    val exchange: String = "NASDAQ",  // NASDAQ, NYSE, AMEX
    val sector: String? = null,        // 업종
    val currency: String = "USD"       // 통화
) : StockData {
    override val identifier: String get() = symbol
}

/**
 * 미국주식 스크리닝 조건
 */
@Serializable
data class USScreeningCondition(
    override val appKey: String,
    override val appSecret: String,
    val isProduction: Boolean = false,  // 실전투자 여부
    override val market: Market = Market.US_NASDAQ,
    val exchangeCode: String = "NAS", // NAS(나스닥), NYS(뉴욕), AMS(아멕스)
    
    // 이동평균선
    override val ma60Enabled: Boolean = false,
    override val ma60Min: Int = 95,
    override val ma60Max: Int = 105,
    override val ma112Enabled: Boolean = true,
    override val ma112Min: Int = 95,
    override val ma112Max: Int = 105,
    override val ma224Enabled: Boolean = false,
    override val ma224Min: Int = 95,
    override val ma224Max: Int = 105,
    
    // 볼린저 밴드
    override val bbEnabled: Boolean = false,
    override val bbPeriod: Int = 20,
    override val bbMultiplier: Double = 2.0,
    override val bbPosition: String = "all",
    override val bbUpperBreak: Boolean = false,
    override val bbLowerBreak: Boolean = false,
    
    // 거래량
    override val volumeEnabled: Boolean = false,
    override val volumeMultiple: Double = 1.5,
    
    // 가격 변동
    override val priceChangeEnabled: Boolean = false,
    override val priceChangeMin: Double = -100.0,
    override val priceChangeMax: Double = 100.0,
    
    // 제외 조건
    val excludeETF: Boolean = true,  // QQQ, SPY 등
    
    // 재무 비율
    override val marketCapEnabled: Boolean = false,
    override val marketCapMin: Long = 0,
    override val marketCapMax: Long = Long.MAX_VALUE,
    override val perEnabled: Boolean = false,
    override val perMin: Double = 0.0,
    override val perMax: Double = 30.0,
    override val pbrEnabled: Boolean = false,
    override val pbrMin: Double = 0.0,
    override val pbrMax: Double = 3.0,
    
    // 이평선 정배열
    override val maAlignment: Boolean = false,
    
    // 대상 종목
    override val targetCodes: List<String> = emptyList()
) : ScreeningCondition

/**
 * 한국투자증권 해외주식 API 응답 - 일별시세
 */
@Serializable
data class KISUSPriceResponse(
    val rt_cd: String,
    val msg_cd: String,
    val msg1: String,
    val output1: List<KISUSPriceOutput> = emptyList(),
    val output2: KISUSPriceInfo? = null
)

@Serializable
data class KISUSPriceOutput(
    val xymd: String,      // 일자 (YYYYMMDD)
    val clos: String,      // 종가
    val open: String,      // 시가
    val high: String,      // 고가
    val low: String,       // 저가
    val tvol: String       // 거래량
)

@Serializable
data class KISUSPriceInfo(
    val rsym: String,      // 실시간 심볼
    val base: String? = null  // 기준가
)

/**
 * 한국투자증권 해외주식 API 응답 - 현재가
 */
@Serializable
data class KISUSCurrentPriceResponse(
    val rt_cd: String,
    val msg_cd: String,
    val msg1: String,
    val output: KISUSCurrentPriceOutput? = null
)

@Serializable
data class KISUSCurrentPriceOutput(
    val last: String,      // 현재가
    val base: String,      // 전일종가
    val rate: String,      // 등락률
    val tvol: String,      // 거래량
    val t_xprc: String? = null,  // 시가총액
    val per: String? = null,
    val pbr: String? = null
)

/**
 * 한국투자증권 API 응답 - 토큰 (공통)
 */
@Serializable
data class KISTokenResponse(
    val access_token: String,
    val token_type: String,
    val expires_in: Int
)
