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
import io.ktor.http.content.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

@Serializable
data class StockMasterUploadResponse(
    val message: String,
    val kospiCount: Int,
    val kosdaqCount: Int,
    val totalCount: Int
)

@Serializable
data class DatabaseStatusResponse(
    val initialized: Boolean,
    val totalStocks: Int,
    val totalRecords: Int,
    val oldestDate: String?,
    val newestDate: String?,
    val lastInit: String?,
    val lastUpdate: String?
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
    // Database 초기화 (한 번만)
    val globalDatabase = com.jeromeent.stockhunter.db.PriceDatabase()
    com.jeromeent.stockhunter.client.StockMasterLoader.setDatabase(globalDatabase)
    
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
        databaseRoutes()            // DB 초기화 및 관리
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
        
        // POST /api/v1/screen - 스크리닝 실행 (DB 기반)
        post("/screen") {
            try {
                val condition = call.receive<ScreeningCondition>()
                
                logger.info { "Received screening request (DB-based)" }
                
                // DB 초기화
                val database = com.jeromeent.stockhunter.db.PriceDatabase()
                
                // DB가 비어있으면 에러
                val stats = database.getStatistics()
                if (stats.totalStocks == 0) {
                    database.close()
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse(error = "Database not initialized. Please initialize the database first.")
                    )
                    return@post
                }
                
                // API 클라이언트 생성 (기본정보 조회용)
                val kisClient = KISApiClient(
                    appKey = condition.appKey,
                    appSecret = condition.appSecret,
                    isProduction = condition.isProduction
                )
                
                // DB 기반 스크리너 실행
                val screener = com.jeromeent.stockhunter.service.DBStockScreener(database, kisClient)
                val result = screener.screen(condition)
                
                // 리소스 정리
                database.close()
                kisClient.close()
                
                logger.info { "Screening completed: ${result.matchedCount} matches" }
                
                call.respond(HttpStatusCode.OK, result)
                
            } catch (e: Exception) {
                logger.error(e) { "Screening failed: ${e.message}" }
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(error = e.message ?: "Screening failed")
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

/**
 * 데이터베이스 관리 라우트
 */
fun Route.databaseRoutes() {
    route("/api/v1/database") {
        
        // GET /api/v1/database/progress - 초기화 진행률 조회
        get("/progress") {
            try {
                val progress = com.jeromeent.stockhunter.db.InitializationProgress.getStatus()
                call.respond(HttpStatusCode.OK, progress)
            } catch (e: Exception) {
                logger.error(e) { "Failed to get progress" }
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(error = e.message ?: "Unknown error")
                )
            }
        }
        
        // GET /api/v1/database/stock-master/stats - 종목 마스터 통계
        get("/stock-master/stats") {
            try {
                val database = com.jeromeent.stockhunter.db.PriceDatabase()
                val stats = database.getStockMasterStats()
                database.close()
                
                call.respond(HttpStatusCode.OK, stats)
            } catch (e: Exception) {
                logger.error(e) { "Failed to get stock master stats" }
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(error = e.message ?: "Unknown error")
                )
            }
        }
        
        // GET /api/v1/database/status - DB 상태 조회
        get("/status") {
            try {
                val database = com.jeromeent.stockhunter.db.PriceDatabase()
                val stats = database.getStatistics()
                
                val isInitialized = stats.totalStocks > 0
                val lastUpdate = database.getMetadata("last_daily_update")
                val lastInit = database.getMetadata("last_full_init")
                
                call.respond(
                    HttpStatusCode.OK,
                    DatabaseStatusResponse(
                        initialized = isInitialized,
                        totalStocks = stats.totalStocks,
                        totalRecords = stats.totalRecords,
                        oldestDate = stats.oldestDate?.toString(),
                        newestDate = stats.newestDate?.toString(),
                        lastInit = lastInit,
                        lastUpdate = lastUpdate
                    )
                )
                
                database.close()
            } catch (e: Exception) {
                logger.error(e) { "Failed to get DB status" }
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(error = e.message ?: "Unknown error")
                )
            }
        }
        
        // POST /api/v1/database/initialize - DB 초기화 시작
        post("/initialize") {
            try {
                @Serializable
                data class InitRequest(
                    val appKey: String,
                    val appSecret: String,
                    val isProduction: Boolean = false,
                    val forceRebuild: Boolean = false  // 강제 재구축 플래그
                )
                
                val request = call.receive<InitRequest>()
                
                // ⚠️ 중복 구축 방지: 이미 초기화되었는지 확인
                val database = com.jeromeent.stockhunter.db.PriceDatabase()
                val stats = database.getStatistics()
                val lastInit = database.getMetadata("last_full_init")
                database.close()
                
                if (stats.totalStocks > 0 && !request.forceRebuild) {
                    logger.warn { "⚠️ Database already initialized with ${stats.totalStocks} stocks" }
                    logger.warn { "Last initialized: $lastInit" }
                    
                    call.respond(
                        HttpStatusCode.Conflict,
                        mapOf(
                            "error" to "Database already initialized",
                            "totalStocks" to stats.totalStocks,
                            "lastInit" to lastInit,
                            "message" to "Use forceRebuild=true to rebuild, or use /update endpoint for daily updates"
                        )
                    )
                    return@post
                }
                
                if (request.forceRebuild) {
                    logger.warn { "⚠️ Force rebuild requested - existing data will be kept and updated" }
                }
                
                logger.info { "🚀 Starting database initialization..." }
                
                // 비동기로 초기화 시작  
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val database = com.jeromeent.stockhunter.db.PriceDatabase()
                        val kisClient = KISApiClient(
                            appKey = request.appKey,
                            appSecret = request.appSecret,
                            isProduction = request.isProduction
                        )
                        
                        val collector = com.jeromeent.stockhunter.db.PriceDataCollector(
                            kisApiClient = kisClient,
                            database = database
                        )
                        
                        // 전체 종목 로드
                        val stockCodes = com.jeromeent.stockhunter.client.StockMasterLoader.loadAllStockCodes()
                        
                        logger.info { "Loading ${stockCodes.size} stocks into database..." }
                        
                        // 초기화 실행 (2~3분 소요)
                        collector.initializeFullDatabase(
                            stockCodes = stockCodes,
                            forceRebuild = request.forceRebuild
                        )
                        
                        database.close()
                        kisClient.close()
                        
                        logger.info { "✅ Database initialization completed!" }
                        
                    } catch (e: Exception) {
                        logger.error(e) { "❌ Database initialization failed" }
                    }
                }
                
                call.respond(
                    HttpStatusCode.Accepted,
                    mapOf(
                        "message" to "Database initialization started",
                        "estimatedTime" to "15-20 minutes"
                    )
                )
                
            } catch (e: Exception) {
                logger.error(e) { "Failed to start initialization" }
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(error = e.message ?: "Unknown error")
                )
            }
        }
        
        // POST /api/v1/database/update - 일일 업데이트
        post("/update") {
            try {
                @Serializable
                data class UpdateRequest(
                    val appKey: String,
                    val appSecret: String,
                    val isProduction: Boolean = false
                )
                
                val request = call.receive<UpdateRequest>()
                
                logger.info { "📅 Starting daily update..." }
                
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val database = com.jeromeent.stockhunter.db.PriceDatabase()
                        
                        val kisClient = KISApiClient(
                            appKey = request.appKey,
                            appSecret = request.appSecret,
                            isProduction = request.isProduction
                        )
                        
                        val collector = com.jeromeent.stockhunter.db.PriceDataCollector(
                            kisApiClient = kisClient,
                            database = database
                        )
                        
                        // 일일 업데이트 실행 (진행률 표시)
                        collector.updateDailyData()
                        
                        database.close()
                        kisClient.close()
                        
                        logger.info { "✅ Daily update completed!" }
                        
                    } catch (e: Exception) {
                        logger.error(e) { "❌ Daily update failed" }
                    }
                }
                
                call.respond(
                    HttpStatusCode.Accepted,
                    mapOf("message" to "Daily update started")
                )
                
            } catch (e: Exception) {
                logger.error(e) { "Failed to start update" }
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(error = e.message ?: "Unknown error")
                )
            }
        }
        
        // POST /api/v1/database/sync-stock-names - 종목명 동기화
        post("/sync-stock-names") {
            try {
                @Serializable
                data class SyncRequest(
                    val appKey: String,
                    val appSecret: String,
                    val isProduction: Boolean = false
                )
                
                val request = call.receive<SyncRequest>()
                logger.info { "🔄 Starting stock name sync..." }
                
                // 즉시 응답
                call.respond(HttpStatusCode.Accepted, mapOf("message" to "Sync started"))
                
                // 백그라운드 작업
                Thread {
                    val db = com.jeromeent.stockhunter.db.PriceDatabase()
                    val client = KISApiClient(request.appKey, request.appSecret, request.isProduction)
                    
                    val codes = db.getAllStockCodes()
                    var success = 0
                    
                    codes.forEachIndexed { idx, code ->
                        try {
                            Thread.sleep(70) // Rate limit (초당 14건)
                            val name = kotlinx.coroutines.runBlocking { 
                                client.getStockNameFromAPI(code) 
                            }
                            
                            if (!name.isNullOrBlank()) {
                                // UPDATE 쿼리 실행
                                db.connection?.prepareStatement(
                                    "UPDATE stock_master SET stock_name = ?, updated_at = ? WHERE stock_code = ?"
                                )?.use { stmt ->
                                    stmt.setString(1, name)
                                    stmt.setString(2, java.time.LocalDateTime.now().toString())
                                    stmt.setString(3, code)
                                    stmt.executeUpdate()
                                }
                                success++
                                if (success % 100 == 0) {
                                    logger.info { "🔄 Progress: $success/${codes.size} (${(success * 100.0 / codes.size).toInt()}%)" }
                                }
                            }
                        } catch (e: Exception) {
                            logger.warn { "[$code] ${e.message}" }
                        }
                    }
                    
                    db.close()
                    client.close()
                    logger.info { "✅ Sync completed: $success/${codes.size}" }
                }.start()
                
            } catch (e: Exception) {
                logger.error(e) { "Sync failed" }
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(error = e.message ?: "Error"))
            }
        }
        
        // POST /api/v1/database/upload-stock-master - 종목 마스터 파일 업로드
        post("/upload-stock-master") {
            try {
                val multipart = call.receiveMultipart()
                val database = com.jeromeent.stockhunter.db.PriceDatabase()
                
                var kospiCount = 0
                var kosdaqCount = 0
                
                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FileItem -> {
                            val fileName = part.originalFileName ?: "unknown"
                            val market = when {
                                fileName.contains("kospi", ignoreCase = true) -> "KOSPI"
                                fileName.contains("kosdaq", ignoreCase = true) -> "KOSDAQ"
                                else -> "UNKNOWN"
                            }
                            
                            if (market != "UNKNOWN") {
                                val fileContent = part.streamProvider().bufferedReader().use { it.readText() }
                                val stocks = com.jeromeent.stockhunter.client.KISStockMasterParser.parseStockMasterFile(
                                    fileContent,
                                    market
                                )
                                
                                // DB에 저장 (List<Triple> -> Map 변환)
                                // Triple: (종목코드, 종목명, 시장)
                                val stocksMap = stocks.associate { it.first to Pair(it.second, it.third) }
                                database.refreshStockMaster(stocksMap)
                                
                                if (market == "KOSPI") {
                                    kospiCount = stocks.size
                                    logger.info { "✅ Uploaded KOSPI: ${stocks.size} stocks" }
                                } else {
                                    kosdaqCount = stocks.size
                                    logger.info { "✅ Uploaded KOSDAQ: ${stocks.size} stocks" }
                                }
                            }
                        }
                        else -> {}
                    }
                    part.dispose()
                }
                
                database.close()
                
                call.respond(
                    HttpStatusCode.OK,
                    StockMasterUploadResponse(
                        message = "Stock master files uploaded successfully",
                        kospiCount = kospiCount,
                        kosdaqCount = kosdaqCount,
                        totalCount = kospiCount + kosdaqCount
                    )
                )
                
            } catch (e: Exception) {
                logger.error(e) { "Failed to upload stock master files" }
                call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(error = e.message ?: "Unknown error")
                )
            }
        }
    }
}
