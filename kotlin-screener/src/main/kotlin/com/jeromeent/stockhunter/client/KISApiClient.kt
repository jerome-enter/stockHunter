package com.jeromeent.stockhunter.client

import com.google.common.util.concurrent.RateLimiter
import com.jeromeent.stockhunter.model.KISPriceResponse
import com.jeromeent.stockhunter.model.KISTokenResponse
import com.jeromeent.stockhunter.model.KISCurrentPriceResponse
import com.jeromeent.stockhunter.model.SearchInfoResponse
import com.jeromeent.stockhunter.model.FinancialRatioResponse
import com.jeromeent.stockhunter.model.FinancialRatioOutput
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import java.time.Instant
import java.time.temporal.ChronoUnit

private val logger = KotlinLogging.logger {}

/**
 * 한국투자증권 OpenAPI 클라이언트
 * 
 * 주요 기능:
 * - OAuth 토큰 관리 (자동 갱신)
 * - Rate Limiting (초당 20건)
 * - 일별 시세 조회
 * - 전체 종목 코드 조회
 */
class KISApiClient(
    private val appKey: String,
    private val appSecret: String,
    private val isProduction: Boolean = false
) {
    private val baseUrl = if (isProduction) {
        "https://openapi.koreainvestment.com:9443"
    } else {
        "https://openapivts.koreainvestment.com:29443" // 모의투자
    }
    
    // Rate Limiter: 초당 20건 제한
    private val rateLimiter = RateLimiter.create(20.0)
    
    // Access Token 캐시
    private var cachedToken: String? = null
    private var tokenExpireTime: Instant? = null
    private val tokenMutex = Mutex()  // Race Condition 방지
    
    /**
     * 토큰 만료 에러 감지
     * 
     * 한투 API 토큰 만료 시 응답 코드:
     * - rt_cd: "1" (에러)
     * - msg_cd: 토큰 관련 에러 코드
     * - HTTP Status: 500 또는 401
     */
    private fun isTokenExpiredError(rtCd: String?, msgCd: String?, msg1: String?): Boolean {
        // rt_cd가 1이고, 메시지나 코드에 토큰/인증 관련 키워드가 있으면 토큰 만료로 판단
        if (rtCd != "1") return false
        
        val errorIndicators = listOf(
            "token", "TOKEN", "인증", "auth", "AUTH", 
            "expired", "EXPIRED", "만료", "invalid", "INVALID"
        )
        
        return errorIndicators.any { keyword ->
            (msgCd?.contains(keyword, ignoreCase = true) == true) ||
            (msg1?.contains(keyword, ignoreCase = true) == true)
        }
    }
    
    // HTTP 클라이언트
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                prettyPrint = true
            })
        }
        
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.INFO
        }
        
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis = 30_000
        }
    }
    
    /**
     * Access Token 발급
     * 
     * 한국투자증권 정책:
     * - 토큰 유효기간: 24시간
     * - 1일 1회 발급 권장
     * - 파일 캐시를 통해 서버 재시작 시에도 토큰 재사용
     * 
     * Race Condition 방지:
     * - Mutex로 동시 요청 시 중복 발급 방지
     */
    suspend fun getAccessToken(): String = tokenMutex.withLock {
        // 1. 메모리 캐시 확인 (빠른 재사용)
        if (cachedToken != null && tokenExpireTime != null) {
            if (Instant.now().isBefore(tokenExpireTime!!.minusSeconds(300))) {
                return cachedToken!!
            }
        }
        
        // 2. 파일 캐시 확인 (서버 재시작 후에도 유지)
        val cachedFromFile = TokenCache.loadToken(appKey, isProduction)
        if (cachedFromFile != null) {
            // 메모리 캐시에도 로드
            cachedToken = cachedFromFile
            // 만료시간은 대략적으로 24시간으로 설정 (정확한 시간은 파일에 저장됨)
            tokenExpireTime = Instant.now().plus(24, ChronoUnit.HOURS)
            logger.info { "✅ Reusing cached token from file (no API call needed)" }
            return cachedFromFile
        }
        
        // 3. 새 토큰 발급 (캐시에 없을 때만)
        logger.info { "⚠️ No valid cached token. Requesting NEW access token from API..." }
        logger.warn { "한국투자증권 API 정책: 1일 1회 토큰 발급 권장. 과도한 발급 시 제한될 수 있습니다." }
        
        try {
            val response = httpClient.post("$baseUrl/oauth2/tokenP") {
                contentType(ContentType.Application.Json)
                setBody(mapOf(
                    "grant_type" to "client_credentials",
                    "appkey" to appKey,
                    "appsecret" to appSecret
                ))
            }
            
            val tokenResponse = response.body<KISTokenResponse>()
            cachedToken = tokenResponse.access_token
            tokenExpireTime = Instant.now().plusSeconds(tokenResponse.expires_in.toLong())
            
            // 파일에 캐시 저장 (서버 재시작 후에도 재사용)
            TokenCache.saveToken(
                appKey = appKey,
                token = tokenResponse.access_token,
                expiresInSeconds = tokenResponse.expires_in,
                isProduction = isProduction
            )
            
            logger.info { "✅ New access token acquired and cached. Expires in ${tokenResponse.expires_in}s (~${tokenResponse.expires_in/3600}h)" }
            return cachedToken!!
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to get access token" }
            throw KISApiException("Failed to authenticate: ${e.message}", e)
        }
    }
    
    /**
     * 일별 주가 조회
     * 
     * @param stockCode 종목코드 (6자리)
     * @param days 조회 일수 (기본 100일, 실제로는 30개만 반환됨 - API 제한)
     * @return 일별 시세 데이터
     */
    suspend fun getDailyPrice(stockCode: String, days: Int = 100): KISPriceResponse {
        rateLimiter.acquire()
        ensureAccessToken()
        
        val trId = if (isProduction) "FHKST01010400" else "FHKST01010400"
        
        try {
            logger.debug { "Fetching daily price for $stockCode" }
            
            val response = httpClient.get("$baseUrl/uapi/domestic-stock/v1/quotations/inquire-daily-price") {
                headers {
                    append("authorization", "Bearer $cachedToken")
                    append("appkey", appKey)
                    append("appsecret", appSecret)
                    append("tr_id", trId)
                }
                parameter("fid_cond_mrkt_div_code", "J") // 주식시장 구분 (J: 전체)
                parameter("fid_input_iscd", stockCode)
                parameter("fid_period_div_code", "D") // 기간 구분 (D: 일)
                parameter("fid_org_adj_prc", "0") // 수정주가 (0: 수정주가 반영)
            }
            
            return response.body<KISPriceResponse>().also {
                if (it.rt_cd != "0") {
                    logger.warn { "API returned non-zero code for $stockCode: ${it.msg1}" }
                }
                logger.debug { "Fetched ${it.getData().size} days of price data for $stockCode" }
            }
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch daily price for $stockCode" }
            throw KISApiException("Failed to get daily price for $stockCode: ${e.message}", e)
        }
    }
    
    /**
     * 기간별 일별 시세 조회 (DB 구축용)
     * 
     * @param stockCode 종목코드 (6자리)
     * @param startDate 시작일 (YYYYMMDD)
     * @param endDate 종료일 (YYYYMMDD)
     * @return 기간 내 일별 시세 데이터
     */
    suspend fun getDailyPriceByPeriod(
        stockCode: String,
        startDate: String,
        endDate: String
    ): KISPriceResponse {
        rateLimiter.acquire()
        ensureAccessToken()
        
        val trId = if (isProduction) "FHKST03010100" else "FHKST03010100"
        
        try {
            logger.debug { "Fetching period price for $stockCode ($startDate ~ $endDate)" }
            
            val response = httpClient.get("$baseUrl/uapi/domestic-stock/v1/quotations/inquire-daily-itemchartprice") {
                headers {
                    append("authorization", "Bearer $cachedToken")
                    append("appkey", appKey)
                    append("appsecret", appSecret)
                    append("tr_id", trId)
                }
                parameter("FID_COND_MRKT_DIV_CODE", "J") // 주식시장 구분 (J: 전체)
                parameter("FID_INPUT_ISCD", stockCode)
                parameter("FID_INPUT_DATE_1", startDate) // 조회 시작일자 (과거)
                parameter("FID_INPUT_DATE_2", endDate) // 조회 종료일자 (최신, 최대 100개)
                parameter("FID_PERIOD_DIV_CODE", "D") // 기간 구분 (D: 일)
                parameter("FID_ORG_ADJ_PRC", "0") // 수정주가 (0: 수정주가 반영)
            }
            
            return response.body<KISPriceResponse>().also {
                val dataSize = it.getData().size
                logger.debug { "API Response: rt_cd=${it.rt_cd}, msg1=${it.msg1}, output.size=${it.output.size}, output2.size=${it.output2.size}, actual=${dataSize}" }
                if (it.rt_cd != "0") {
                    logger.warn { "API returned non-zero code for $stockCode: ${it.msg1}" }
                }
                if (dataSize == 0) {
                    logger.warn { "API returned 0 records for $stockCode: rt_cd=${it.rt_cd}, msg=${it.msg1}" }
                }
                logger.debug { "Fetched ${dataSize} records for $stockCode" }
            }
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch period price for $stockCode" }
            throw KISApiException("Failed to get period price for $stockCode: ${e.message}", e)
        }
    }
    
    /**
     * 종목 코드와 이름 매핑
     */
    companion object {
        private val stockNames = mapOf(
            "005930" to "삼성전자",
            "000660" to "SK하이닉스",
            "035420" to "NAVER",
            "051910" to "LG화학",
            "006400" to "삼성SDI",
            "035720" to "카카오",
            "005380" to "현대차",
            "012330" to "현대모비스",
            "055550" to "신한지주",
            "207940" to "삼성바이오로직스",
            "068270" to "셀트리온",
            "028260" to "삼성물산",
            "015760" to "한국전력",
            "017670" to "SK텔레콤",
            "096770" to "SK이노베이션",
            "000270" to "기아",
            "003670" to "포스코퓨처엠",
            "105560" to "KB금융",
            "034730" to "SK",
            "003550" to "LG",
            "009150" to "삼성전기",
            "010950" to "S-Oil",
            "011170" to "롯데케미칼",
            "032830" to "삼성생명",
            "066570" to "LG전자",
            "086790" to "하나금융지주",
            "018260" to "삼성에스디에스",
            "009540" to "한국조선해양",
            "000810" to "삼성화재",
            "033780" to "KT&G"
        )
    }
    
    /**
     * 전체 종목 코드 조회
     * 
     * 캐시 전략:
     * 1. 캐시 확인 (7일 이내)
     * 2. 없으면 API에서 다운로드
     * 3. 캐시에 저장
     */
    suspend fun getAllStockCodes(): List<String> {
        // 1. 캐시된 마스터 데이터 확인
        val cachedStocks = StockMasterCache.loadMasterData("KOSPI_KOSDAQ")
        if (cachedStocks != null) {
            return cachedStocks.map { it.code }
        }
        
        // 2. 캐시 없으면 다운로드
        logger.info { "Downloading stock master data from API..." }
        val stocks = downloadStockMaster()
        
        // 3. 캐시에 저장
        StockMasterCache.saveMasterData(stocks, "KOSPI_KOSDAQ")
        
        return stocks.map { it.code }
    }
    
    /**
     * 종목 마스터 데이터 다운로드
     * 
     * CSV 파일에서 전체 종목 리스트를 로드합니다.
     * resources/stock_master.csv
     */
    private suspend fun downloadStockMaster(): List<StockInfo> {
        val stocks = mutableListOf<StockInfo>()
        
        try {
            logger.info { "Loading stock master from CSV file..." }
            
            // CSV 파일 읽기
            val csvContent = this::class.java.classLoader
                .getResourceAsStream("stock_master.csv")
                ?.bufferedReader()
                ?.readText()
            
            if (csvContent != null) {
                // CSV 파싱
                csvContent.lines()
                    .drop(1) // 헤더 스킵
                    .filter { it.isNotBlank() }
                    .forEach { line ->
                        val cols = line.split(",")
                        if (cols.size >= 3) {
                            stocks.add(StockInfo(
                                code = cols[0].trim(),
                                name = cols[1].trim(),
                                market = cols[2].trim(),
                                sector = cols.getOrNull(3)?.trim(),
                                isETF = cols[1].contains("ETF", ignoreCase = true),
                                isETN = cols[1].contains("ETN", ignoreCase = true)
                            ))
                        }
                    }
                
                logger.info { "✅ Loaded ${stocks.size} stocks from CSV file" }
            } else {
                throw Exception("CSV file not found in resources")
            }
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to load stock master from CSV" }
            logger.warn { "⚠️ Falling back to hardcoded stock list (30 stocks)" }
            
            // 실패 시 하드코딩된 종목 사용
            stockNames.forEach { (code, name) ->
                stocks.add(StockInfo(
                    code = code,
                    name = name,
                    market = determineMarket(code),
                    isETF = name.contains("ETF"),
                    isETN = name.contains("ETN")
                ))
            }
        }
        
        return stocks
    }
    
    /**
     * 종목코드로 시장 구분 판단 (간단한 휴리스틱)
     */
    private fun determineMarket(code: String): String {
        return when {
            code.startsWith("00") || code.startsWith("005") -> "KOSPI"
            else -> "KOSDAQ"
        }
    }
    
    /**
     * 종목코드로 종목명 조회 (캐시 전용, 동기)
     * 
     * 1. 캐시된 마스터에서 찾기
     * 2. 하드코딩된 맵에서 찾기
     * 3. 없으면 코드 그대로 반환
     */
    fun getStockName(code: String): String {
        // 캐시에서 찾기
        val cached = StockMasterCache.loadMasterData("KOSPI_KOSDAQ")
        if (cached != null) {
            val stock = cached.find { it.code == code }
            if (stock != null) return stock.name
        }
        
        // 하드코딩된 맵에서 찾기
        return stockNames[code] ?: code
    }
    
    /**
     * 종목검색 API로 종목명 조회
     * 
     * API: /uapi/domestic-stock/v1/quotations/search-info
     * TR_ID: CTPF1604R
     * 
     * @param stockCode 종목코드 (6자리)
     * @return 종목 약칭명 (예: "삼성전자", "SK하이닉스") 또는 null
     */
    suspend fun getStockNameFromAPI(stockCode: String): String? {
        rateLimiter.acquire()
        ensureAccessToken()
        
        try {
            logger.debug { "Fetching stock name for $stockCode" }
            
            val response = httpClient.get("$baseUrl/uapi/domestic-stock/v1/quotations/search-info") {
                headers {
                    append("authorization", "Bearer $cachedToken")
                    append("appkey", appKey)
                    append("appsecret", appSecret)
                    append("tr_id", "CTPF1604R")
                    append("custtype", "P")
                }
                parameter("PRDT_TYPE_CD", "300")  // 주식
                parameter("PDNO", stockCode)
            }
            
            val result = response.body<SearchInfoResponse>()
            
            if (result.rt_cd == "0") {
                // prdt_abrv_name (상품약칭명) 반환 (예: "삼성전자")
                return result.output?.prdt_abrv_name?.takeIf { it.isNotBlank() }
            } else {
                logger.warn { "Search-info API error for $stockCode: ${result.msg1}" }
                return null
            }
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch stock name for $stockCode" }
            return null
        }
    }
    
    /**
     * 종목 현재가 시세 조회 (기본정보 포함)
     * PER, PBR, 시가총액 등 포함
     */
    suspend fun getCurrentPriceWithInfo(stockCode: String): KISCurrentPriceResponse? {
        rateLimiter.acquire()
        ensureAccessToken()
        
        val trId = if (isProduction) "FHKST01010100" else "FHKST01010100"
        
        try {
            logger.info { "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" }
            logger.info { "📊 현재가 조회 시작" }
            logger.info { "  - URL: $baseUrl/uapi/domestic-stock/v1/quotations/inquire-price" }
            logger.info { "  - 종목코드: $stockCode" }
            logger.info { "  - TR_ID: $trId" }
            logger.info { "  - 파라미터: fid_cond_mrkt_div_code=J, fid_input_iscd=$stockCode" }
            logger.info { "  - 헤더:" }
            logger.info { "    * Content-Type: application/json; charset=utf-8" }
            logger.info { "    * authorization: Bearer ${cachedToken?.take(20)}..." }
            logger.info { "    * appkey: ${appKey.take(10)}..." }
            logger.info { "    * appsecret: ${appSecret.take(10)}..." }
            logger.info { "    * tr_id: $trId" }
            logger.info { "    * custtype: P" }
            logger.info { "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" }
            
            val response = httpClient.get("$baseUrl/uapi/domestic-stock/v1/quotations/inquire-price") {
                headers {
                    append("Content-Type", "application/json; charset=utf-8")
                    append("authorization", "Bearer $cachedToken")
                    append("appkey", appKey)
                    append("appsecret", appSecret)
                    append("tr_id", trId)
                    append("custtype", "P")  // 개인 고객 타입
                }
                parameter("fid_cond_mrkt_div_code", "J")
                parameter("fid_input_iscd", stockCode)
            }
            
            val result = response.body<KISCurrentPriceResponse>()
            
            logger.info { "📥 응답 수신 완료" }
            logger.info { "  - HTTP Status: ${response.status}" }
            logger.info { "  - rt_cd: ${result.rt_cd}" }
            logger.info { "  - msg1: ${result.msg1}" }
            logger.info { "  - msg_cd: ${result.msg_cd}" }
            
            // 토큰 만료 체크 및 자동 갱신
            if (isTokenExpiredError(result.rt_cd, result.msg_cd, result.msg1)) {
                logger.warn { "⚠️ 토큰 만료 감지! 새 토큰 발급 후 재시도..." }
                TokenCache.clearToken(appKey, isProduction)
                cachedToken = null
                tokenExpireTime = null
                // 재귀 호출로 새 토큰 발급 후 재시도 (무한 루프 방지: 1회만)
                return getCurrentPriceWithInfo(stockCode)
            }
            
            if (result.rt_cd != "0") {
                logger.error { "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" }
                logger.error { "❌ API 에러 발생!" }
                logger.error { "  - rt_cd: ${result.rt_cd}" }
                logger.error { "  - msg1: ${result.msg1}" }
                logger.error { "  - msg_cd: ${result.msg_cd}" }
                logger.error { "  - output: ${result.output}" }
                logger.error { "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" }
            } else {
                logger.info { "✅ API 호출 성공!" }
                logger.info { "  - 현재가: ${result.output?.stck_prpr}원" }
                logger.info { "  - 전일대비: ${result.output?.prdy_vrss}원" }
                logger.info { "  - 등락률: ${result.output?.prdy_ctrt}%" }
                logger.info { "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" }
            }
            
            return result
            
        } catch (e: Exception) {
            logger.error { "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" }
            logger.error { "💥 예외 발생!" }
            logger.error(e) { "[$stockCode] Exception while fetching current price" }
            logger.error { "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" }
            return null
        }
    }
    
    /**
     * Rate Limit 고려한 일괄 조회
     */
    suspend fun getDailyPriceBatch(stockCodes: List<String>, days: Int = 100): Map<String, KISPriceResponse> {
        val results = mutableMapOf<String, KISPriceResponse>()
        
        stockCodes.forEach { code ->
            try {
                val response = getDailyPrice(code, days)
                results[code] = response
                
                // API 안정성을 위한 추가 딜레이
                delay(50) // 50ms 대기
                
            } catch (e: Exception) {
                logger.warn { "Failed to fetch data for $code: ${e.message}" }
            }
        }
        
        return results
    }
    
    /**
     * 토큰 유효성 확인 및 갱신
     */
    private suspend fun ensureAccessToken() {
        if (cachedToken == null || tokenExpireTime == null || 
            Instant.now().isAfter(tokenExpireTime!!.minusSeconds(300))) {
            getAccessToken()
        }
    }
    
    /**
     * 재무비율 조회 (P, R 조건용)
     * 
     * @param code 종목코드 (6자리)
     * @return 재무비율 정보 (부채비율, 유보율 등)
     */
    suspend fun getFinancialRatio(code: String): FinancialRatioOutput? {
        rateLimiter.acquire()
        ensureAccessToken()
        
        return try {
            // 재무비율 API가 작동하지 않으므로 현재가 시세 API 사용 (EPS, BPS만 제공)
            val response = httpClient.get("$baseUrl/uapi/domestic-stock/v1/quotations/inquire-price") {
                headers {
                    append("authorization", "Bearer $cachedToken")
                    append("appkey", appKey)
                    append("appsecret", appSecret)
                    append("tr_id", "FHKST01010100") // 주식 현재가 시세
                }
                parameter("FID_COND_MRKT_DIV_CODE", "J") // 주식
                parameter("FID_INPUT_ISCD", code)
            }.body<KISCurrentPriceResponse>()
            
            logger.info { "[$code] Successfully fetched current price for financial data, rt_cd: ${response.rt_cd}" }
            
            val output = response.output
            if (output != null && response.rt_cd == "0") {
                // EPS, BPS만 제공 가능, 부채비율/유보율은 별도 API 필요
                logger.info { "[$code] Financial data - eps: ${output.eps}, bps: ${output.bps}, per: ${output.per}, pbr: ${output.pbr}" }
                FinancialRatioOutput(
                    debt_ratio = null, // 현재가 API에서 제공 안 됨
                    rsrv_rate = null,  // 현재가 API에서 제공 안 됨
                    eps = output.eps,
                    bps = output.bps,
                    roe = null,
                    roa = null
                )
            } else {
                logger.warn { "[$code] No output or error in current price response, rt_cd: ${response.rt_cd}, msg: ${response.msg1}" }
                null
            }
        } catch (e: Exception) {
            logger.error(e) { "[$code] Error fetching financial ratio" }
            null
        }
    }
    
    /**
     * 리소스 정리
     */
    fun close() {
        httpClient.close()
        logger.info { "KIS API Client closed" }
    }
}

/**
 * 커스텀 예외
 */
class KISApiException(message: String, cause: Throwable? = null) : Exception(message, cause)
