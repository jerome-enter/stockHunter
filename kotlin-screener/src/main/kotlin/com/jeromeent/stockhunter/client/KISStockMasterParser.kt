package com.jeromeent.stockhunter.client

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * 한국투자증권 종목마스터 파일 파서
 * 
 * 파일 포맷:
 * - 위치 0-5: 종목코드 (6자리, 예: 005930)
 * - 위치 9-20: ISIN 코드 (12자리)
 * - 위치 21-60: 종목명 (40자, 공백 패딩)
 * - 위치 61-63: 시장구분 (ST1=KOSPI, ST2=KOSDAQ 추정)
 */
object KISStockMasterParser {
    
    private val httpClient = HttpClient(CIO) {
        engine {
            requestTimeout = 30_000
        }
    }
    
    /**
     * 한투 종목마스터 파일에서 전체 종목 조회
     * 
     * @param kospiUrl KOSPI 종목마스터 파일 URL
     * @param kosdaqUrl KOSDAQ 종목마스터 파일 URL
     * @return (종목코드, 시장구분) 리스트
     */
    suspend fun fetchAllStockCodes(
        kospiUrl: String,
        kosdaqUrl: String
    ): List<Pair<String, String>> {
        logger.info { "📥 Fetching stock master files from KIS..." }
        
        val allStocks = mutableListOf<Pair<String, String>>()
        
        // KOSPI 조회
        try {
            val kospiStocks = fetchAndParseFile(kospiUrl, "KOSPI")
            allStocks.addAll(kospiStocks)
            logger.info { "✅ Fetched ${kospiStocks.size} stocks from KOSPI master file" }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to fetch KOSPI master file" }
        }
        
        // KOSDAQ 조회
        try {
            val kosdaqStocks = fetchAndParseFile(kosdaqUrl, "KOSDAQ")
            allStocks.addAll(kosdaqStocks)
            logger.info { "✅ Fetched ${kosdaqStocks.size} stocks from KOSDAQ master file" }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to fetch KOSDAQ master file" }
        }
        
        logger.info { "✅ Total fetched: ${allStocks.size} stocks (KOSPI + KOSDAQ)" }
        return allStocks
    }
    
    /**
     * 파일 다운로드 및 파싱
     */
    private suspend fun fetchAndParseFile(url: String, market: String): List<Pair<String, String>> {
        val response: HttpResponse = httpClient.get(url)
        val content = response.bodyAsText()
        
        return parseStockMasterFile(content, market)
    }
    
    /**
     * 종목마스터 파일 파싱
     * 
     * 각 라인 포맷:
     * 005930   KR7005930003삼성전자                                ST1...
     * 
     * @param content 파일 내용 (전체 텍스트)
     * @param market 시장 구분 (KOSPI/KOSDAQ)
     * @return (종목코드, 시장) 리스트
     */
    fun parseStockMasterFile(content: String, market: String): List<Pair<String, String>> {
        return content.lines()
            .filter { it.length >= 6 }  // 최소 6자리 이상
            .mapNotNull { line ->
                try {
                    // 앞 6자리 = 종목코드
                    val stockCode = line.substring(0, 6).trim()
                    
                    // 숫자 6자리인지 검증
                    if (stockCode.matches(Regex("\\d{6}"))) {
                        stockCode to market
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    null
                }
            }
            .distinct()  // 중복 제거
    }
    
    /**
     * 단일 URL로 KOSPI + KOSDAQ 통합 파일 조회
     */
    suspend fun fetchAllStockCodesFromSingleFile(url: String): List<Pair<String, String>> {
        logger.info { "📥 Fetching combined stock master file from: $url" }
        
        try {
            val response: HttpResponse = httpClient.get(url)
            val content = response.bodyAsText()
            
            // 파일 내에서 시장 구분 추출 (ST1=KOSPI, ST2=KOSDAQ 등)
            val stocks = content.lines()
                .filter { it.length >= 64 }
                .mapNotNull { line ->
                    try {
                        val stockCode = line.substring(0, 6).trim()
                        val marketCode = if (line.length >= 64) line.substring(61, 64).trim() else ""
                        
                        if (stockCode.matches(Regex("\\d{6}"))) {
                            val market = when {
                                marketCode.startsWith("ST1") -> "KOSPI"
                                marketCode.startsWith("ST2") -> "KOSDAQ"
                                else -> "UNKNOWN"
                            }
                            
                            if (market != "UNKNOWN") {
                                stockCode to market
                            } else {
                                null
                            }
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
                .distinct()
            
            logger.info { "✅ Fetched ${stocks.size} stocks from master file" }
            return stocks
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to fetch stock master file" }
            return emptyList()
        }
    }
}
