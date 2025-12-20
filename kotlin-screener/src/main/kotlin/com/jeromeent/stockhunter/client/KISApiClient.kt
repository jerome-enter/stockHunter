package com.jeromeent.stockhunter.client

import com.google.common.util.concurrent.RateLimiter
import com.jeromeent.stockhunter.model.KISPriceResponse
import com.jeromeent.stockhunter.model.KISTokenResponse
import com.jeromeent.stockhunter.model.KISCurrentPriceResponse
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
        
        defaultRequest {
            contentType(ContentType.Application.Json)
        }
    }
    
    /**
     * Access Token 발급
     * 
     * 한국투자증권 정책:
     * - 토큰 유효기간: 24시간
     * - 1일 1회 발급 권장
     * - 파일 캐시를 통해 서버 재시작 시에도 토큰 재사용
     */
    suspend fun getAccessToken(): String {
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
     * @param days 조회 일수 (최대 100일)
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
            }
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch daily price for $stockCode" }
            throw KISApiException("Failed to get daily price for $stockCode: ${e.message}", e)
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
     * 종목코드로 종목명 조회
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
     * 종목 현재가 시세 조회 (기본정보 포함)
     * PER, PBR, 시가총액 등 포함
     */
    suspend fun getCurrentPriceWithInfo(stockCode: String): KISCurrentPriceResponse? {
        rateLimiter.acquire()
        ensureAccessToken()
        
        val trId = if (isProduction) "FHKST01010100" else "FHKST01010100"
        
        try {
            logger.debug { "Fetching current price with info for $stockCode" }
            
            val response = httpClient.get("$baseUrl/uapi/domestic-stock/v1/quotations/inquire-price") {
                headers {
                    append("authorization", "Bearer $cachedToken")
                    append("appkey", appKey)
                    append("appsecret", appSecret)
                    append("tr_id", trId)
                }
                parameter("fid_cond_mrkt_div_code", "J")
                parameter("fid_input_iscd", stockCode)
            }
            
            return response.body<KISCurrentPriceResponse>().also {
                if (it.rt_cd != "0") {
                    logger.warn { "API returned non-zero code for $stockCode: ${it.msg1}" }
                }
            }
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch current price with info for $stockCode" }
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
