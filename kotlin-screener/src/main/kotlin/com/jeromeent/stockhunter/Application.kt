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
import kotlinx.serialization.json.Json
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

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
            mapOf(
                "status" to "healthy",
                "service" to "stock-hunter",
                "version" to "1.0.0",
                "timestamp" to System.currentTimeMillis()
            )
        )
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
                    isProduction = false // 모의투자 환경
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
                    mapOf(
                        "valid" to true,
                        "message" to "Credentials validated successfully"
                    )
                )
                
            } catch (e: Exception) {
                logger.warn { "Credential validation failed: ${e.message}" }
                call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf(
                        "valid" to false,
                        "message" to (e.message ?: "Invalid credentials")
                    )
                )
            }
        }
        
        // GET /api/v1/stock-codes - 지원 종목 코드 조회
        get("/stock-codes") {
            val kisClient = KISApiClient("", "", isProduction = false)
            val codes = kisClient.getAllStockCodes()
            
            call.respond(
                HttpStatusCode.OK,
                mapOf(
                    "codes" to codes,
                    "count" to codes.size
                )
            )
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
                    mapOf("error" to (e.message ?: "US screening failed"))
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
                    mapOf(
                        "symbols" to symbols,
                        "count" to symbols.size,
                        "exchange" to exchange
                    )
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to e.message)
                )
            }
        }
    }
}
