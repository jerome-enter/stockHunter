package com.jeromeent.stockhunter.us.client

import com.google.common.util.concurrent.RateLimiter
import com.jeromeent.stockhunter.us.model.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * 한국투자증권 해외주식 API 클라이언트
 */
class KISUSApiClient(
    private val appKey: String,
    private val appSecret: String,
    private val isProduction: Boolean = false
) {
    private val baseUrl = if (isProduction) {
        "https://openapi.koreainvestment.com:9443"
    } else {
        "https://openapivts.koreainvestment.com:29443"
    }
    
    private val rateLimiter = RateLimiter.create(20.0)
    private var cachedToken: String? = null
    private var tokenExpireTime: Instant? = null
    
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.INFO
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
        }
    }
    
    /**
     * Access Token 발급 (국내와 동일)
     */
    suspend fun getAccessToken(): String {
        if (cachedToken != null && tokenExpireTime != null) {
            if (Instant.now().isBefore(tokenExpireTime!!.minusSeconds(300))) {
                return cachedToken!!
            }
        }
        
        logger.info { "Requesting new access token for US stocks..." }
        
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
            
            logger.info { "US stocks access token acquired" }
            return cachedToken!!
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to get access token for US stocks" }
            throw Exception("Failed to authenticate: ${e.message}", e)
        }
    }
    
    /**
     * 미국주식 일별 시세 조회
     */
    suspend fun getDailyPrice(
        symbol: String,
        exchangeCode: String = "NAS",
        days: Int = 100
    ): KISUSPriceResponse {
        rateLimiter.acquire()
        ensureAccessToken()
        
        val trId = if (isProduction) "HHDFS76240000" else "HHDFS76240000"
        
        try {
            logger.debug { "Fetching daily price for US stock: $symbol ($exchangeCode)" }
            
            val response = httpClient.get("$baseUrl/uapi/overseas-price/v1/quotations/dailyprice") {
                headers {
                    append("authorization", "Bearer $cachedToken")
                    append("appkey", appKey)
                    append("appsecret", appSecret)
                    append("tr_id", trId)
                }
                parameter("AUTH", "")
                parameter("EXCD", exchangeCode)  // NAS(나스닥), NYS(뉴욕), AMS(아멕스)
                parameter("SYMB", symbol)
                parameter("GUBN", "0")  // 0: 일봉
                parameter("BYMD", "")   // 공백: 최근 데이터
                parameter("MODP", "0")  // 0: 수정주가 미반영
            }
            
            return response.body<KISUSPriceResponse>().also {
                if (it.rt_cd != "0") {
                    logger.warn { "API returned non-zero code for $symbol: ${it.msg1}" }
                }
            }
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch daily price for $symbol" }
            throw Exception("Failed to get daily price for $symbol: ${e.message}", e)
        }
    }
    
    /**
     * 미국주식 현재가 조회
     */
    suspend fun getCurrentPrice(
        symbol: String,
        exchangeCode: String = "NAS"
    ): KISUSCurrentPriceResponse? {
        rateLimiter.acquire()
        ensureAccessToken()
        
        val trId = if (isProduction) "HHDFS00000300" else "HHDFS00000300"
        
        try {
            val response = httpClient.get("$baseUrl/uapi/overseas-price/v1/quotations/price") {
                headers {
                    append("authorization", "Bearer $cachedToken")
                    append("appkey", appKey)
                    append("appsecret", appSecret)
                    append("tr_id", trId)
                }
                parameter("AUTH", "")
                parameter("EXCD", exchangeCode)
                parameter("SYMB", symbol)
            }
            
            return response.body<KISUSCurrentPriceResponse>()
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch current price for $symbol" }
            return null
        }
    }
    
    /**
     * 미국 주요 종목 심볼 목록
     */
    fun getAllUSSymbols(exchangeCode: String = "NAS"): List<String> {
        return when (exchangeCode) {
            "NAS" -> listOf(
                // NASDAQ 주요 종목
                "AAPL",  // 애플
                "MSFT",  // 마이크로소프트
                "GOOGL", // 구글
                "AMZN",  // 아마존
                "NVDA",  // 엔비디아
                "META",  // 메타
                "TSLA",  // 테슬라
                "AVGO",  // 브로드컴
                "COST",  // 코스트코
                "NFLX",  // 넷플릭스
                "AMD",   // AMD
                "INTC",  // 인텔
                "CSCO",  // 시스코
                "ADBE",  // 어도비
                "CMCSA", // 컴캐스트
                "PEP",   // 펩시코
                "PYPL",  // 페이팔
                "QCOM",  // 퀄컴
                "TXN",   // 텍사스인스트루먼트
                "SBUX"   // 스타벅스
            )
            "NYS" -> listOf(
                // NYSE 주요 종목
                "JPM",   // JP모건
                "V",     // 비자
                "JNJ",   // 존슨앤존슨
                "WMT",   // 월마트
                "MA",    // 마스터카드
                "PG",    // P&G
                "UNH",   // 유나이티드헬스
                "HD",    // 홈디포
                "BAC",   // 뱅크오브아메리카
                "DIS",   // 디즈니
                "VZ",    // 버라이즌
                "ADBE",  // 어도비
                "KO",    // 코카콜라
                "T",     // AT&T
                "XOM",   // 엑손모빌
                "CVX",   // 쉐브론
                "ABT",   // 애보트
                "MRK",   // 머크
                "PFE",   // 화이자
                "ORCL"   // 오라클
            )
            else -> emptyList()
        }
    }
    
    private suspend fun ensureAccessToken() {
        if (cachedToken == null || tokenExpireTime == null || 
            Instant.now().isAfter(tokenExpireTime!!.minusSeconds(300))) {
            getAccessToken()
        }
    }
    
    fun close() {
        httpClient.close()
        logger.info { "KIS US API Client closed" }
    }
}
