package com.jeromeent.stockhunter.client

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import mu.KotlinLogging
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit

private val logger = KotlinLogging.logger {}

/**
 * 종목 정보
 */
@Serializable
data class StockInfo(
    val code: String,           // 종목코드
    val name: String,           // 종목명
    val market: String,         // 시장구분 (KOSPI/KOSDAQ/KONEX)
    val sector: String? = null, // 업종
    val isETF: Boolean = false, // ETF 여부
    val isETN: Boolean = false  // ETN 여부
)

/**
 * 마스터 파일 캐시 데이터
 */
@Serializable
data class StockMasterData(
    val stocks: List<StockInfo>,
    val downloadedAt: Long,     // 다운로드 시각 (Epoch seconds)
    val totalCount: Int
)

/**
 * 종목 마스터 파일 캐시 관리
 * 
 * 정책:
 * - 캐시 유효기간: 7일 (상장/폐지가 자주 발생하지 않음)
 * - 파일 위치: ~/.stockhunter/stock_master_{market}.json
 * - 자동 갱신: 만료 시 자동 다운로드
 */
object StockMasterCache {
    private val cacheDir = File(System.getProperty("user.home"), ".stockhunter")
    private val json = Json { 
        ignoreUnknownKeys = true
        prettyPrint = true 
    }
    
    // 캐시 유효기간 (7일)
    private const val CACHE_VALIDITY_DAYS = 7L
    
    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
            logger.info { "Created stock master cache directory: ${cacheDir.absolutePath}" }
        }
    }
    
    /**
     * 캐시 파일 경로
     */
    private fun getCacheFile(market: String = "ALL"): File {
        return File(cacheDir, "stock_master_${market}.json")
    }
    
    /**
     * 마스터 데이터 저장
     */
    fun saveMasterData(stocks: List<StockInfo>, market: String = "ALL") {
        try {
            val masterData = StockMasterData(
                stocks = stocks,
                downloadedAt = Instant.now().epochSecond,
                totalCount = stocks.size
            )
            
            val cacheFile = getCacheFile(market)
            cacheFile.writeText(json.encodeToString(masterData))
            
            logger.info { 
                "Stock master data cached: ${stocks.size} stocks (${market})" 
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to cache stock master data" }
        }
    }
    
    /**
     * 마스터 데이터 로드
     * 
     * @return 유효한 마스터 데이터 또는 null
     */
    fun loadMasterData(market: String = "ALL"): List<StockInfo>? {
        try {
            val cacheFile = getCacheFile(market)
            
            if (!cacheFile.exists()) {
                logger.debug { "No cached stock master file found" }
                return null
            }
            
            val masterData = json.decodeFromString<StockMasterData>(cacheFile.readText())
            val now = Instant.now().epochSecond
            val age = (now - masterData.downloadedAt) / 86400.0 // days
            
            // 캐시 유효기간 체크 (7일)
            if ((now - masterData.downloadedAt) < (CACHE_VALIDITY_DAYS * 86400)) {
                val downloadedTime = Instant.ofEpochSecond(masterData.downloadedAt)
                
                logger.info { 
                    "Using cached stock master data (${masterData.totalCount} stocks, " +
                    "downloaded: $downloadedTime, age: ${String.format("%.1f", age)} days)" 
                }
                return masterData.stocks
            } else {
                logger.info { 
                    "Cached stock master data expired (age: ${String.format("%.1f", age)} days, " +
                    "max: $CACHE_VALIDITY_DAYS days)" 
                }
                // 만료된 캐시 파일 삭제
                cacheFile.delete()
                return null
            }
            
        } catch (e: Exception) {
            logger.warn(e) { "Failed to load cached stock master data" }
            return null
        }
    }
    
    /**
     * 캐시 강제 삭제
     */
    fun clearCache(market: String = "ALL") {
        try {
            val cacheFile = getCacheFile(market)
            if (cacheFile.exists()) {
                cacheFile.delete()
                logger.info { "Stock master cache cleared: $market" }
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to clear stock master cache" }
        }
    }
    
    /**
     * 모든 마스터 캐시 삭제
     */
    fun clearAllCaches() {
        try {
            cacheDir.listFiles()?.filter { it.name.startsWith("stock_master_") }?.forEach { 
                it.delete()
                logger.info { "Deleted master cache file: ${it.name}" }
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to clear all master caches" }
        }
    }
    
    /**
     * 캐시 상태 정보
     */
    fun getCacheStats(market: String = "ALL"): String {
        return try {
            val cacheFile = getCacheFile(market)
            if (!cacheFile.exists()) {
                "No cached data"
            } else {
                val masterData = json.decodeFromString<StockMasterData>(cacheFile.readText())
                val now = Instant.now().epochSecond
                val age = (now - masterData.downloadedAt) / 86400.0
                val remaining = CACHE_VALIDITY_DAYS - age
                
                """
                Total Stocks: ${masterData.totalCount}
                Downloaded: ${Instant.ofEpochSecond(masterData.downloadedAt)}
                Age: ${String.format("%.1f", age)} days
                Remaining: ${String.format("%.1f", remaining)} days
                Valid Until: ${Instant.ofEpochSecond(masterData.downloadedAt + CACHE_VALIDITY_DAYS * 86400)}
                """.trimIndent()
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}
