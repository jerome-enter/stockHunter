package com.jeromeent.stockhunter

import com.jeromeent.stockhunter.client.KISApiClient
import com.jeromeent.stockhunter.model.ScreeningCondition
import com.jeromeent.stockhunter.service.StockScreener
import com.jeromeent.stockhunter.us.client.KISUSApiClient
import com.jeromeent.stockhunter.us.model.USScreeningCondition
import com.jeromeent.stockhunter.us.service.USStockScreener
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

@Serializable
data class HealthResponse(
    val status: String,
    val service: String,
    val version: String,
    val timestamp: Long
)

@Serializable
data class StockCodesResponse(
    val codes: List<String>,
    val count: Int
)

@Serializable
data class SymbolsResponse(
    val symbols: List<String>,
    val count: Int,
    val exchange: String
)

@Serializable
data class ValidationResponse(
    val valid: Boolean,
    val message: String
)

@Serializable
data class ErrorResponse(
    val error: String
)

fun main() {
    embeddedServer(
        Netty,
        port = 8080,
        host = "0.0.0.0",
        module = Application::module
    ).start(wait = true)
}

fun Application.module() {
    // JSON 설정
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }
    
    // CORS 설정
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Options)
    }
    
    // 에러 핸들링
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            logger.error(cause) { "Unhandled exception: ${cause.message}" }
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf(
                    "error" to (cause.message ?: "Unknown error"),
                    "timestamp" to System.currentTimeMillis()
                )
            )
        }
    }
    
    // 라우팅
    routing {
        healthCheck()
        tokenDebugRoutes()          // 토큰 디버그 (개발용)
        domesticScreeningRoutes()  // 기존 국내주식
        usScreeningRoutes()         // 신규 미국주식
    }
    
    logger.info { "✅ Stock Hunter API Server started on port 8080" }
}

/**
 * Health Check
 */
fun Route.healthCheck() {
    get("/health") {
        call.respond(
            HttpStatusCode.OK,
            HealthResponse(
                status = "healthy",
                service = "stock-hunter",
                version = "1.0.0",
                timestamp = System.currentTimeMillis()
            )
        )
    }
}

/**
 * 토큰 디버그 라우트 (개발용)
 */
fun Route.tokenDebugRoutes() {
    route("/api/v1/debug") {
        get("/token-status") {
            try {
                val appKey = call.request.queryParameters["appKey"] ?: ""
                val isProduction = call.request.queryParameters["production"]?.toBoolean() ?: false
                
                val stats = com.jeromeent.stockhunter.client.TokenCache.getTokenStats(appKey, isProduction)
                
                call.respond(
                    HttpStatusCode.OK,
                    mapOf(
                        "status" to "success",
                        "tokenStats" to stats,
                        "message" to "한국투자증권 API 토큰은 24시간 유효하며, 파일 캐시를 통해 재사용됩니다."
                    )
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.OK,
                    mapOf(
                        "status" to "no_token",
                        "message" to "캐시된 토큰이 없습니다. 첫 API 호출 시 자동으로 발급됩니다."
                    )
                )
            }
        }
        
        delete("/clear-token-cache") {
            try {
                com.jeromeent.stockhunter.client.TokenCache.clearAllTokens()
                call.respond(
                    HttpStatusCode.OK,
                    mapOf("status" to "success", "message" to "All token caches cleared")
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(error = e.message ?: "Failed to clear cache")
                )
            }
        }
        
        get("/master-status") {
            try {
                val stats = com.jeromeent.stockhunter.client.StockMasterCache.getCacheStats("KOSPI_KOSDAQ")
                
                call.respond(
                    HttpStatusCode.OK,
                    mapOf(
                        "status" to "success",
                        "masterStats" to stats,
                        "message" to "종목 마스터는 7일간 캐시되며, CSV 파일에서 자동 로드됩니다."
                    )
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.OK,
                    mapOf(
                        "status" to "no_master",
                        "message" to "캐시된 마스터가 없습니다. 첫 스크리닝 시 자동으로 로드됩니다."
                    )
                )
            }
        }
        
        delete("/clear-master-cache") {
            try {
                com.jeromeent.stockhunter.client.StockMasterCache.clearAllCaches()
                call.respond(
                    HttpStatusCode.OK,
                    mapOf("status" to "success", "message" to "All master caches cleared")
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(error = e.message ?: "Failed to clear master cache")
                )
            }
        }
    }
}

/**
 * 국내주식 스크리닝 라우트
 */
fun Route.domesticScreeningRoutes() {
    route("/api/v1") {
        
        // POST /api/v1/screen - 스크리닝 실행
        post("/screen") {
            try {
                val condition = call.receive<ScreeningCondition>()
                
                logger.info { "Received screening request: ${condition.targetCodes.size} targets" }
                
                // API 클라이언트 생성
                val kisClient = KISApiClient(
                    appKey = condition.appKey,
                    appSecret = condition.appSecret,
                    isProduction = condition.isProduction  // 사용자가 선택한 환경
                )
                
                // 스크리너 실행
                val screener = StockScreener(kisClient)
                val result = screener.screen(condition)
                
                // 리소스 정리
                kisClient.close()
                
                logger.info { "Screening completed: ${result.matchedCount} matches" }
                
                call.respond(HttpStatusCode.OK, result)
                
            } catch (e: Exception) {
                logger.error(e) { "Screening failed: ${e.message}" }
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to (e.message ?: "Screening failed"))
                )
            }
        }
        
        // POST /api/v1/validate-credentials - API 키 검증
        post("/validate-credentials") {
            try {
                val body = call.receive<Map<String, String>>()
                val appKey = body["appKey"] ?: throw IllegalArgumentException("Missing appKey")
                val appSecret = body["appSecret"] ?: throw IllegalArgumentException("Missing appSecret")
                
                val kisClient = KISApiClient(
                    appKey = appKey,
                    appSecret = appSecret,
                    isProduction = false
                )
                
                // 토큰 발급 시도
                val token = kisClient.getAccessToken()
                kisClient.close()
                
                call.respond(
                    HttpStatusCode.OK,
                    ValidationResponse(
                        valid = true,
                        message = "Credentials validated successfully"
                    )
                )
                
            } catch (e: Exception) {
                logger.warn { "Credential validation failed: ${e.message}" }
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ValidationResponse(
                        valid = false,
                        message = e.message ?: "Invalid credentials"
                    )
                )
            }
        }
        
        // GET /api/v1/stock-codes - 지원 종목 코드 조회
        get("/stock-codes") {
            try {
                val kisClient = KISApiClient("", "", isProduction = false)
                val codes = kisClient.getAllStockCodes()
                
                call.respond(
                    HttpStatusCode.OK,
                    StockCodesResponse(
                        codes = codes,
                        count = codes.size
                    )
                )
            } catch (e: Exception) {
                logger.error { "종목 코드 조회 실패: ${e.message}" }
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(error = e.message ?: "Unknown error")
                )
            }
        }
    }
}

/**
 * 미국주식 스크리닝 라우트
 */
fun Route.usScreeningRoutes() {
    route("/api/v1/us") {
        
        // POST /api/v1/us/screen - 미국주식 스크리닝
        post("/screen") {
            try {
                val condition = call.receive<USScreeningCondition>()
                
                logger.info { "Received US screening request: ${condition.exchangeCode}" }
                
                // API 클라이언트 생성
                val kisClient = KISUSApiClient(
                    appKey = condition.appKey,
                    appSecret = condition.appSecret,
                    isProduction = false
                )
                
                // 스크리너 실행
                val screener = USStockScreener(kisClient)
                val result = screener.screen(condition)
                
                // 리소스 정리
                kisClient.close()
                
                logger.info { "US screening completed: ${result.matchedCount} matches" }
                
                call.respond(HttpStatusCode.OK, result)
                
            } catch (e: Exception) {
                logger.error(e) { "US screening failed: ${e.message}" }
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(error = e.message ?: "US screening failed")
                )
            }
        }
        
        // GET /api/v1/us/symbols - 미국 주요 종목 심볼
        get("/symbols") {
            try {
                val exchange = call.request.queryParameters["exchange"] ?: "NAS"
                val kisClient = KISUSApiClient("", "", isProduction = false)
                val symbols = kisClient.getAllUSSymbols(exchange)
                
                call.respond(
                    HttpStatusCode.OK,
                    SymbolsResponse(
                        symbols = symbols,
                        count = symbols.size,
                        exchange = exchange
                    )
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(error = e.message ?: "Unknown error")
                )
            }
        }
    }
}
