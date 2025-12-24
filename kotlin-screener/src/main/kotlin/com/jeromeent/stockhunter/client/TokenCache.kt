package com.jeromeent.stockhunter.client

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import mu.KotlinLogging
import java.io.File
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * 토큰 캐시 데이터
 */
@Serializable
data class CachedTokenData(
    val token: String,
    val expiresAt: Long,  // Epoch seconds
    val issuedAt: Long    // Epoch seconds - 발급 시각 추적
)

/**
 * 파일 기반 토큰 캐시 관리
 * 
 * 한국투자증권 API 정책:
 * - Access Token 유효기간: 24시간
 * - 1일 1회 발급 권장 (과도한 발급 시 제한)
 * - 토큰 재사용 필수
 */
object TokenCache {
    private val cacheDir = File(System.getProperty("user.home"), ".stockhunter")
    private val json = Json { 
        ignoreUnknownKeys = true
        prettyPrint = true 
    }
    
    init {
        // 캐시 디렉토리 생성
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
            logger.info { "Created token cache directory: ${cacheDir.absolutePath}" }
        }
    }
    
    /**
     * 캐시 파일 경로 생성
     */
    private fun getCacheFile(appKey: String, isProduction: Boolean): File {
        val env = if (isProduction) "prod" else "dev"
        val keyHash = appKey.hashCode().toString(16)
        return File(cacheDir, "token_${env}_${keyHash}.json")
    }
    
    /**
     * 토큰 저장
     */
    fun saveToken(
        appKey: String, 
        token: String, 
        expiresInSeconds: Int,
        isProduction: Boolean = false
    ) {
        try {
            val now = Instant.now().epochSecond
            val cacheData = CachedTokenData(
                token = token,
                expiresAt = now + expiresInSeconds,
                issuedAt = now
            )
            
            val cacheFile = getCacheFile(appKey, isProduction)
            cacheFile.writeText(json.encodeToString(cacheData))
            
            logger.info { 
                "Token cached to file. Expires at: ${Instant.ofEpochSecond(cacheData.expiresAt)}" 
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to cache token to file" }
        }
    }
    
    /**
     * 토큰 로드
     * 
     * @return 유효한 토큰 또는 null
     */
    fun loadToken(appKey: String, isProduction: Boolean = false): String? {
        try {
            val cacheFile = getCacheFile(appKey, isProduction)
            
            if (!cacheFile.exists()) {
                logger.debug { "No cached token file found" }
                return null
            }
            
            val cacheData = json.decodeFromString<CachedTokenData>(cacheFile.readText())
            val now = Instant.now().epochSecond
            
            // API 응답 기반 토큰 갱신을 사용하므로, 만료 시간까지 그냥 사용
            // (API가 토큰 만료를 알려주면 그때 갱신)
            if (now < cacheData.expiresAt) {
                val issuedTime = Instant.ofEpochSecond(cacheData.issuedAt)
                val expiresTime = Instant.ofEpochSecond(cacheData.expiresAt)
                val remainingHours = (cacheData.expiresAt - now) / 3600.0
                
                logger.info { 
                    "Using cached token (issued: $issuedTime, expires: $expiresTime, remaining: ${String.format("%.1f", remainingHours)}h)" 
                }
                return cacheData.token
            } else {
                logger.info { "Cached token expired" }
                // 만료된 캐시 파일 삭제
                cacheFile.delete()
                return null
            }
            
        } catch (e: Exception) {
            logger.warn(e) { "Failed to load cached token" }
            return null
        }
    }
    
    /**
     * 토큰 캐시 삭제
     */
    fun clearToken(appKey: String, isProduction: Boolean = false) {
        try {
            val cacheFile = getCacheFile(appKey, isProduction)
            if (cacheFile.exists()) {
                cacheFile.delete()
                logger.info { "Token cache cleared" }
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to clear token cache" }
        }
    }
    
    /**
     * 모든 캐시 파일 정리 (개발용)
     */
    fun clearAllTokens() {
        try {
            cacheDir.listFiles()?.filter { it.name.startsWith("token_") }?.forEach { 
                it.delete()
                logger.info { "Deleted cache file: ${it.name}" }
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to clear all token caches" }
        }
    }
    
    /**
     * 토큰 발급 통계 (디버깅용)
     */
    fun getTokenStats(appKey: String, isProduction: Boolean = false): String {
        return try {
            val cacheFile = getCacheFile(appKey, isProduction)
            if (!cacheFile.exists()) {
                "No cached token"
            } else {
                val cacheData = json.decodeFromString<CachedTokenData>(cacheFile.readText())
                val now = Instant.now().epochSecond
                val age = (now - cacheData.issuedAt) / 3600.0
                val remaining = (cacheData.expiresAt - now) / 3600.0
                
                """
                Token Age: ${String.format("%.1f", age)}h
                Remaining: ${String.format("%.1f", remaining)}h
                Issued At: ${Instant.ofEpochSecond(cacheData.issuedAt)}
                Expires At: ${Instant.ofEpochSecond(cacheData.expiresAt)}
                """.trimIndent()
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }
}
