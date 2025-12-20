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
     */
    suspend fun getAccessToken(): String {
        // 캐시된 토큰이 유효한 경우 재사용
        if (cachedToken != null && tokenExpireTime != null) {
            if (Instant.now().isBefore(tokenExpireTime!!.minusSeconds(300))) {
                return cachedToken!!
            }
        }
        
        logger.info { "Requesting new access token..." }
        
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
            
            logger.info { "Access token acquired successfully. Expires in ${tokenResponse.expires_in}s" }
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
     * 전체 종목 코드 조회 (하드코딩 - 실제로는 마스터 파일 다운로드 필요)
     * 
     * TODO: 실제 운영에서는 종목 마스터 파일을 다운로드하여 사용
     * @see https://apiportal.koreainvestment.com/apiservice-apiservice
     */
    fun getAllStockCodes(): List<String> {
        // 코스피 주요 종목 (예시)
        return listOf(
            // 시가총액 상위
            "005930", // 삼성전자
            "000660", // SK하이닉스
            "035420", // NAVER
            "051910", // LG화학
            "006400", // 삼성SDI
            "035720", // 카카오
            "005380", // 현대차
            "012330", // 현대모비스
            "055550", // 신한지주
            "207940", // 삼성바이오로직스
            
            // 주요 대형주
            "068270", // 셀트리온
            "028260", // 삼성물산
            "015760", // 한국전력
            "017670", // SK텔레콤
            "096770", // SK이노베이션
            "000270", // 기아
            "003670", // 포스코퓨처엠
            "105560", // KB금융
            "034730", // SK
            "003550", // LG
            
            // 중형주
            "009150", // 삼성전기
            "010950", // S-Oil
            "011170", // 롯데케미칼
            "032830", // 삼성생명
            "066570", // LG전자
            "086790", // 하나금융지주
            "018260", // 삼성에스디에스
            "009540", // 한국조선해양
            "000810", // 삼성화재
            "033780"  // KT&G
        )
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
