package com.jeromeent.stockhunter.us.client

import com.google.common.util.concurrent.RateLimiter
import com.jeromeent.stockhunter.client.TokenCache
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
     * 파일 캐시를 통해 토큰 재사용
     */
    suspend fun getAccessToken(): String {
        // 1. 메모리 캐시 확인
        if (cachedToken != null && tokenExpireTime != null) {
            if (Instant.now().isBefore(tokenExpireTime!!.minusSeconds(300))) {
                return cachedToken!!
            }
        }
        
        // 2. 파일 캐시 확인
        val cachedFromFile = TokenCache.loadToken(appKey, isProduction)
        if (cachedFromFile != null) {
            cachedToken = cachedFromFile
            tokenExpireTime = Instant.now().plus(24, java.time.temporal.ChronoUnit.HOURS)
            logger.info { "✅ Reusing cached US stocks token from file" }
            return cachedFromFile
        }
        
        // 3. 새 토큰 발급
        logger.info { "⚠️ Requesting new access token for US stocks..." }
        
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
            
            // 파일에 캐시
            TokenCache.saveToken(appKey, tokenResponse.access_token, tokenResponse.expires_in, isProduction)
            
            logger.info { "✅ US stocks access token acquired and cached" }
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
     * 미국 종목 심볼과 이름 매핑
     */
    companion object {
        private val usStockNames = mapOf(
            // NASDAQ
            "AAPL" to "애플",
            "MSFT" to "마이크로소프트",
            "GOOGL" to "구글",
            "AMZN" to "아마존",
            "NVDA" to "엔비디아",
            "META" to "메타",
            "TSLA" to "테슬라",
            "AVGO" to "브로드컴",
            "COST" to "코스트코",
            "NFLX" to "넷플릭스",
            "AMD" to "AMD",
            "INTC" to "인텔",
            "CSCO" to "시스코",
            "ADBE" to "어도비",
            "CMCSA" to "컴캐스트",
            "PEP" to "펩시코",
            "PYPL" to "페이팔",
            "QCOM" to "퀄컴",
            "TXN" to "텍사스인스트루먼트",
            "SBUX" to "스타벅스",
            // NYSE
            "JPM" to "JP모건",
            "V" to "비자",
            "JNJ" to "존슨앤존슨",
            "WMT" to "월마트",
            "MA" to "마스터카드",
            "PG" to "P&G",
            "UNH" to "유나이티드헬스",
            "HD" to "홈디포",
            "BAC" to "뱅크오브아메리카",
            "DIS" to "디즈니",
            "VZ" to "버라이즌",
            "KO" to "코카콜라",
            "T" to "AT&T",
            "XOM" to "엑손모빌",
            "CVX" to "쉐브론",
            "ABT" to "애보트",
            "MRK" to "머크",
            "PFE" to "화이자",
            "NKE" to "나이키",
            "LLY" to "일라이릴리"
        )
    }
    
    /**
     * 미국 주요 종목 심볼 목록
     */
    fun getAllUSSymbols(exchangeCode: String = "NAS"): List<String> {
        return when (exchangeCode) {
            "NAS" -> usStockNames.keys.filter { it in listOf(
                "AAPL", "MSFT", "GOOGL", "AMZN", "NVDA", "META", "TSLA", 
                "AVGO", "COST", "NFLX", "AMD", "INTC", "CSCO", "ADBE",
                "CMCSA", "PEP", "PYPL", "QCOM", "TXN", "SBUX"
            )}
            "NYS" -> usStockNames.keys.filter { it in listOf(
                "JPM", "V", "JNJ", "WMT", "MA", "PG", "UNH", "HD", "BAC",
                "DIS", "VZ", "KO", "T", "XOM", "CVX", "ABT", "MRK", "PFE", "NKE", "LLY"
            )}
            else -> emptyList()
        }
    }
    
    /**
     * 심볼로 종목명 조회
     */
    fun getUSStockName(symbol: String): String {
        return usStockNames[symbol] ?: symbol
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
