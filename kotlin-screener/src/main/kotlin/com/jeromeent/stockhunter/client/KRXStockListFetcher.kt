package com.jeromeent.stockhunter.client

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * 네이버 금융 API를 통해 전체 상장 종목 리스트를 가져옵니다.
 * 
 * 공개 API이므로 인증 불필요
 */
object KRXStockListFetcher {
    
    private val httpClient = HttpClient(CIO) {
        engine {
            requestTimeout = 30_000
        }
    }
    
    /**
     * 전체 코스피 + 코스닥 종목 코드 조회
     * 
     * @return 6자리 종목코드 리스트 (예: "005930")
     */
    suspend fun fetchAllStockCodes(): List<String> {
        logger.info { "📥 Fetching all stock codes from Naver Finance..." }
        
        return try {
            val kospiStocks = fetchMarketStocksFromNaver("KOSPI")
            val kosdaqStocks = fetchMarketStocksFromNaver("KOSDAQ")
            
            val allStocks = (kospiStocks + kosdaqStocks).distinct().sorted()
            
            logger.info { "✅ Fetched ${allStocks.size} stocks (KOSPI: ${kospiStocks.size}, KOSDAQ: ${kosdaqStocks.size})" }
            
            allStocks
        } catch (e: Exception) {
            logger.error(e) { "❌ Failed to fetch stock list from Naver" }
            emptyList()
        }
    }
    
    /**
     * 네이버 금융에서 특정 시장의 종목 리스트 조회
     * 
     * @param market "KOSPI" 또는 "KOSDAQ"
     */
    suspend fun fetchMarketStocksFromNaver(market: String): List<String> {
        try {
            // 네이버 금융 시세 페이지에서 전체 종목 조회
            val url = "https://finance.naver.com/sise/sise_market_sum.naver"
            
            val response = httpClient.get(url) {
                parameter("sosok", if (market == "KOSPI") "0" else "1")
                parameter("page", "1")
                headers {
                    append(HttpHeaders.UserAgent, "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
                    append(HttpHeaders.Accept, "text/html,application/xhtml+xml,application/xml")
                }
            }
            
            if (response.status != HttpStatusCode.OK) {
                logger.warn { "Failed to fetch $market stocks: ${response.status}" }
                return emptyList()
            }
            
            val html = response.bodyAsText()
            
            // HTML 파싱: href="/item/main.naver?code=005930" 패턴 추출
            val stockCodes = parseStockCodesFromHTML(html)
            
            val allStocks = stockCodes.toMutableList()
            
            // 네이버는 페이지당 50개씩 표시
            // KOSPI: ~900개 = 18페이지
            // KOSDAQ: ~1600개 = 32페이지
            // 안전하게 각각 35페이지씩 조회
            val maxPages = 35
            
            logger.info { "Fetching $market stocks from pages 1-$maxPages..." }
            
            // 2페이지부터 마지막까지 조회
            for (page in 2..maxPages) {
                try {
                    val pageResponse = httpClient.get(url) {
                        parameter("sosok", if (market == "KOSPI") "0" else "1")
                        parameter("page", page.toString())
                        headers {
                            append(HttpHeaders.UserAgent, "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
                        }
                    }
                    
                    if (pageResponse.status == HttpStatusCode.OK) {
                        val pageHtml = pageResponse.bodyAsText()
                        val pageCodes = parseStockCodesFromHTML(pageHtml)
                        allStocks.addAll(pageCodes)
                    }
                    
                    // 너무 빠르게 요청하지 않도록 딜레이
                    kotlinx.coroutines.delay(100)
                } catch (e: Exception) {
                    logger.warn { "Failed to fetch page $page: ${e.message}" }
                }
            }
            
            logger.debug { "Fetched ${allStocks.distinct().size} stocks from $market" }
            
            return allStocks.distinct()
        } catch (e: Exception) {
            logger.error(e) { "Error fetching $market stocks from Naver" }
            return emptyList()
        }
    }
    
    /**
     * HTML에서 종목코드 추출
     * 
     * 패턴: href="/item/main.naver?code=005930"
     */
    private fun parseStockCodesFromHTML(html: String): List<String> {
        val stockCodes = mutableListOf<String>()
        
        // code=XXXXXX 패턴 추출
        val pattern = Regex("""code=(\d{6})""")
        
        pattern.findAll(html).forEach { match ->
            val code = match.groupValues[1]
            stockCodes.add(code)
        }
        
        return stockCodes.distinct()
    }
    
}
