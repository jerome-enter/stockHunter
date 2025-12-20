package com.jeromeent.stockhunter.model

import kotlinx.serialization.Serializable

/**
 * 주식 데이터 모델
 */
@Serializable
data class StockData(
    val code: String,
    val name: String,
    val currentPrice: Double,
    val changePercent: Double,
    val volume: Long,
    val ma5: Double? = null,
    val ma20: Double? = null,
    val ma60: Double? = null,
    val ma112: Double? = null,
    val ma224: Double? = null,
    val ma60Ratio: Double? = null,
    val ma112Ratio: Double? = null,
    val ma224Ratio: Double? = null,
    val bollingerBands: BollingerBands? = null,
    val bbPosition: BBPosition = BBPosition.MIDDLE,
    val volumeRatio: Double? = null,
    val maAlignment: Boolean = false,
    val marketCap: Long? = null,
    val per: Double? = null,
    val pbr: Double? = null,
    val roe: Double? = null,
    val foreignRatio: Double? = null,  // 외국인 보유비율
    val status: StockStatus = StockStatus.NORMAL
)

/**
 * 일별 시세 데이터
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
    UPPER,   // 상단 밴드 근처/돌파
    MIDDLE,  // 중간 밴드 근처
    LOWER    // 하단 밴드 근처/터치
}

/**
 * 종목 상태
 */
@Serializable
enum class StockStatus {
    NORMAL,          // 정상
    MANAGEMENT,      // 관리종목
    WARNING,         // 투자경고
    SUSPENDED,       // 거래정지
    DELISTING        // 상장폐지
}

/**
 * 스크리닝 조건
 */
@Serializable
data class ScreeningCondition(
    // API 인증
    val appKey: String,
    val appSecret: String,
    val isProduction: Boolean = false,  // 실전투자 여부
    
    // 이동평균선 조건
    val ma60Enabled: Boolean = false,
    val ma60Min: Int = 95,
    val ma60Max: Int = 105,
    
    val ma112Enabled: Boolean = true,
    val ma112Min: Int = 95,
    val ma112Max: Int = 105,
    
    val ma224Enabled: Boolean = false,
    val ma224Min: Int = 95,
    val ma224Max: Int = 105,
    
    // 볼린저 밴드
    val bbEnabled: Boolean = false,
    val bbPeriod: Int = 20,
    val bbMultiplier: Double = 2.0,
    val bbPosition: String = "all", // all, upper, middle, lower
    val bbUpperBreak: Boolean = false,
    val bbLowerBreak: Boolean = false,
    
    // 거래량
    val volumeEnabled: Boolean = false,
    val volumeMultiple: Double = 1.5,
    
    // 가격 변동
    val priceChangeEnabled: Boolean = false,
    val priceChangeMin: Double = -100.0,
    val priceChangeMax: Double = 100.0,
    
    // 제외 조건
    val excludeETF: Boolean = true,
    val excludeETN: Boolean = true,
    val excludeManagement: Boolean = false,
    
    // 시가총액
    val marketCapEnabled: Boolean = false,
    val marketCapMin: Long = 0,
    val marketCapMax: Long = Long.MAX_VALUE,
    
    // 재무 비율
    val perEnabled: Boolean = false,
    val perMin: Double = 0.0,
    val perMax: Double = 30.0,
    
    val pbrEnabled: Boolean = false,
    val pbrMin: Double = 0.0,
    val pbrMax: Double = 3.0,
    
    val roeEnabled: Boolean = false,
    val roeMin: Double = 0.0,
    
    // 외국인/기관
    val foreignRatioEnabled: Boolean = false,
    val foreignRatioMin: Double = 0.0,
    
    // 이평선 정배열
    val maAlignment: Boolean = false,
    
    // 종목 코드 (비어있으면 전체 검색)
    val targetCodes: List<String> = emptyList()
)

/**
 * 스크리닝 결과
 */
@Serializable
data class ScreeningResult(
    val stocks: List<StockData>,
    val totalScanned: Int,
    val matchedCount: Int,
    val executionTimeMs: Long,
    val timestamp: String
)

/**
 * API 응답 - 일별 시세
 */
@Serializable
data class KISPriceResponse(
    val rt_cd: String,
    val msg_cd: String,
    val msg1: String,
    val output: List<KISPriceOutput> = emptyList()
)

@Serializable
data class KISPriceOutput(
    val stck_bsop_date: String,  // 주식 영업 일자
    val stck_clpr: String,        // 주식 종가
    val stck_oprc: String,        // 주식 시가
    val stck_hgpr: String,        // 주식 최고가
    val stck_lwpr: String,        // 주식 최저가
    val acml_vol: String,         // 누적 거래량
    val acml_tr_pbmn: String? = null,  // 누적 거래 대금
    val prdy_vrss: String? = null,     // 전일 대비
    val prdy_vrss_sign: String? = null, // 전일 대비 부호
    val prdy_ctrt: String? = null,     // 전일 대비율
    val hts_kor_isnm: String? = null   // HTS 한글 종목명
)

/**
 * API 응답 - 토큰
 */
@Serializable
data class KISTokenResponse(
    val access_token: String,
    val token_type: String,
    val expires_in: Int
)

/**
 * API 응답 - 주식현재가 시세 (기본정보 포함)
 */
@Serializable
data class KISCurrentPriceResponse(
    val rt_cd: String,
    val msg_cd: String,
    val msg1: String,
    val output: KISCurrentPriceOutput? = null
)

@Serializable
data class KISCurrentPriceOutput(
    val stck_prpr: String,        // 주식 현재가
    val stck_shrn_iscd: String,   // 주식 단축 종목코드
    val prdy_vrss: String,        // 전일 대비
    val prdy_vrss_sign: String,   // 전일 대비 부호
    val prdy_ctrt: String,        // 전일 대비율
    val hts_avls: String? = null, // 시가총액 (HTS 시가총액)
    val per: String? = null,      // PER
    val pbr: String? = null,      // PBR
    val eps: String? = null,      // EPS
    val bps: String? = null,      // BPS
    val itewhol_loan_rmnd_ratem_name: String? = null  // 신용잔고율
)
