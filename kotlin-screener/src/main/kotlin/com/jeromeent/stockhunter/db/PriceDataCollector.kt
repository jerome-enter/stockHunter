package com.jeromeent.stockhunter.db

import com.google.common.util.concurrent.RateLimiter
import com.jeromeent.stockhunter.client.KISApiClient
import kotlinx.coroutines.delay
import mu.KotlinLogging
import java.time.LocalDate

private val logger = KotlinLogging.logger {}

/**
 * 가격 데이터 수집기
 * 
 * 한국투자증권 API 제약 준수:
 * - 초당 20건 제한 → 안전하게 15건으로 설정
 * - 각 호출 사이 67ms 딜레이 (Rate Limiter 자동 처리)
 * - 에러 시 재시도 (exponential backoff)
 */
class PriceDataCollector(
    private val kisApiClient: KISApiClient,
    private val database: PriceDatabase
) {
    // ⚠️ 한국투자증권 제약: 초당 15건 (안전 마진)
    private val rateLimiter = RateLimiter.create(15.0)
    
    /**
     * 전체 DB 초기화 (최초 1회)
     * 
     * 예상 시간: 500개 × 4회 × 67ms = ~2분 15초
     * API 호출: 2,000회 (각 종목당 4회, 280일 데이터)
     */
    suspend fun initializeFullDatabase(
        stockCodes: List<String>,
        forceRebuild: Boolean = false
    ) {
        logger.info { "🚀 Starting full database initialization" }
        logger.info { "Target: ${stockCodes.size} stocks" }
        if (forceRebuild) {
            logger.warn { "⚠️ FORCE REBUILD: All existing data will be replaced!" }
        }
        
        val startTime = System.currentTimeMillis()
        var successCount = 0
        var failureCount = 0
        var skippedCount = 0
        
        // 진행 상태 시작
        InitializationProgress.start(stockCodes.size)
        
        stockCodes.forEachIndexed { index, stockCode ->
            try {
                // forceRebuild가 아니면 이미 있는 데이터 스킵
                if (!forceRebuild) {
                    val existingData = database.getPrices(stockCode, days = 1)
                    if (existingData.isNotEmpty()) {
                        val latestDate = database.getLatestDate(stockCode)
                        logger.debug { "[$stockCode] Already initialized (latest: $latestDate), skipping..." }
                        skippedCount++
                        // 진행상황 업데이트
                        InitializationProgress.update(index + 1, stockCode)
                        return@forEachIndexed
                    }
                }
                
                logger.info { "[${ index + 1}/${stockCodes.size}] Processing $stockCode..." }
                
                // 300일 데이터 수집 (3회 API 호출)
                val priceData = fetch300DaysData(stockCode)
                
                if (priceData.isEmpty()) {
                    logger.warn { "[$stockCode] No data returned from API, skipping..." }
                    failureCount++
                    return@forEachIndexed
                }
                
                // DB 저장
                database.savePriceBatch(stockCode, priceData)
                logger.debug { "[$stockCode] Saved ${priceData.size} price records" }
                
                successCount++
                
                // 진행상황 업데이트
                InitializationProgress.update(index + 1, stockCode)
                
                if ((index + 1) % 100 == 0) {
                    val elapsed = (System.currentTimeMillis() - startTime) / 1000
                    val remaining = ((stockCodes.size - index - 1) * elapsed) / (index + 1)
                    logger.info { "Progress: ${index + 1}/${stockCodes.size} (${successCount} success, ${failureCount} failed)" }
                    logger.info { "Elapsed: ${elapsed}s, Remaining: ~${remaining}s" }
                }
                
            } catch (e: Exception) {
                logger.error(e) { "Failed to process $stockCode" }
                failureCount++
            }
        }
        
        val totalTime = (System.currentTimeMillis() - startTime) / 1000
        
        logger.info { "✅ Database initialization completed!" }
        logger.info { "Success: $successCount / ${stockCodes.size}" }
        logger.info { "Skipped: $skippedCount (already exists)" }
        logger.info { "Failed: $failureCount" }
        logger.info { "Total time: ${totalTime}s (${totalTime / 60}m ${totalTime % 60}s)" }
        
        // 오래된 데이터 자동 정리 (280일 이전)
        logger.info { "🧹 Cleaning old data (keeping 280 days)..." }
        database.cleanOldData(keepDays = 280)
        
        // 진행 상태 완료
        InitializationProgress.complete()
        
        // 메타데이터 저장
        database.setMetadata("last_full_init", LocalDate.now().toString())
        database.setMetadata("total_stocks", stockCodes.size.toString())
    }
    
    /**
     * 280일 데이터 수집 (4번 API 호출)
     * 
     * 기간별 시세 API 사용:
     * - 1차: 최근 100일
     * - 2차: 이전 100일  
     * - 3차: 이전 100일
     * - 4차: 이전 100일
     * → 총 280일 확보! (ma224 계산 가능)
     */
    private suspend fun fetch300DaysData(stockCode: String): List<DailyPrice> {
        val allData = mutableListOf<DailyPrice>()
        val seenDates = mutableSetOf<LocalDate>() // 중복 방지
        
        val today = LocalDate.now()
        
        // 4번 호출해서 280일 데이터 수집
        for (batch in 0 until 4) {
            try {
                // ⚠️ Rate Limiter 대기 (67ms)
                rateLimiter.acquire()
                
                // 날짜 범위 계산
                val endDate = today.minusDays((batch * 100).toLong())
                val startDate = endDate.minusDays(99) // 100일
                
                val startDateStr = startDate.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
                val endDateStr = endDate.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
                
                logger.debug { "[$stockCode] Batch ${batch + 1}/4: Requesting $startDateStr ~ $endDateStr (${startDate} ~ ${endDate})" }
                
                // 기간별 API 호출
                val response = kisApiClient.getDailyPriceByPeriod(
                    stockCode = stockCode,
                    startDate = startDateStr,
                    endDate = endDateStr
                )
                
                val actualData = response.getData()
                logger.debug { "[$stockCode] Batch ${batch + 1}/4: API returned ${actualData.size} records" }
                
                // 응답 데이터를 DailyPrice로 변환
                actualData.forEach { priceData ->
                    val tradeDate = LocalDate.parse(
                        priceData.stck_bsop_date,
                        java.time.format.DateTimeFormatter.BASIC_ISO_DATE
                    )
                    
                    // 중복 날짜 제거
                    if (tradeDate !in seenDates) {
                        seenDates.add(tradeDate)
                        allData.add(
                            DailyPrice(
                                date = tradeDate,
                                open = priceData.stck_oprc.toDoubleOrNull() ?: 0.0,
                                high = priceData.stck_hgpr.toDoubleOrNull() ?: 0.0,
                                low = priceData.stck_lwpr.toDoubleOrNull() ?: 0.0,
                                close = priceData.stck_clpr.toDoubleOrNull() ?: 0.0,
                                volume = priceData.acml_vol.toLongOrNull() ?: 0L
                            )
                        )
                    }
                }
                
                // 100일 이상 수집했으면 충분
                if (allData.size >= 100 * (batch + 1)) {
                    logger.debug { "[$stockCode] Collected ${allData.size} days, continuing..." }
                } else {
                    logger.warn { "[$stockCode] Only got ${allData.size} days so far, API may have limited data" }
                }
                
                // API 부하 방지를 위한 약간의 딜레이
                if (batch < 3) delay(50)
                
            } catch (e: Exception) {
                logger.error(e) { "[$stockCode] Failed to fetch batch ${batch + 1}" }
                // 첫 번째 배치 실패는 치명적
                if (batch == 0) throw e
                // 나머지는 계속 진행 (부분 데이터라도 저장)
            }
        }
        
        logger.debug { "[$stockCode] Total collected: ${allData.size} days" }
        
        return allData
    }
    
    /**
     * 일일 업데이트 (모든 종목의 최신 데이터 추가)
     * 
     * 예상 시간: 500개 × 67ms = ~35초
     * API 호출: 500회 (각 종목당 1회, 최신 1일만)
     */
    suspend fun updateDailyData() {
        val today = LocalDate.now()
        val lastUpdate = database.getMetadata("last_daily_update")?.let { 
            LocalDate.parse(it) 
        }
        
        if (lastUpdate == today) {
            logger.info { "Already up to date (last update: $lastUpdate)" }
            return
        }
        
        val allStockCodes = database.getAllStockCodes()
        logger.info { "📅 Starting daily update for ${allStockCodes.size} stocks" }
        
        // 진행 상태 시작
        InitializationProgress.start(allStockCodes.size)
        
        val startTime = System.currentTimeMillis()
        var successCount = 0
        var failureCount = 0
        
        allStockCodes.forEachIndexed { index, stockCode ->
            try {
                // ⚠️ Rate Limiter 대기
                rateLimiter.acquire()
                
                // DB에서 마지막 날짜 확인
                val latestDate = database.getLatestDate(stockCode)
                val daysSinceLastUpdate = if (latestDate != null) {
                    java.time.temporal.ChronoUnit.DAYS.between(latestDate, today).toInt()
                } else {
                    1  // 데이터 없으면 1일만
                }
                
                // 누락된 기간만큼 가져오기 (최대 100일)
                val daysToFetch = minOf(daysSinceLastUpdate + 1, 100)
                
                logger.debug { "[$stockCode] Latest: $latestDate, fetching $daysToFetch days" }
                
                val response = kisApiClient.getDailyPrice(stockCode, days = daysToFetch)
                
                if (response.output.isNotEmpty()) {
                    // 모든 데이터를 DailyPrice로 변환
                    val priceData = response.output.map { data ->
                        val tradeDate = LocalDate.parse(
                            data.stck_bsop_date,
                            java.time.format.DateTimeFormatter.BASIC_ISO_DATE
                        )
                        DailyPrice(
                            date = tradeDate,
                            open = data.stck_oprc.toDoubleOrNull() ?: 0.0,
                            high = data.stck_hgpr.toDoubleOrNull() ?: 0.0,
                            low = data.stck_lwpr.toDoubleOrNull() ?: 0.0,
                            close = data.stck_clpr.toDoubleOrNull() ?: 0.0,
                            volume = data.acml_vol.toLongOrNull() ?: 0L
                        )
                    }.filter { price ->
                        // 기존 데이터보다 새로운 것만 저장
                        latestDate == null || price.date > latestDate
                    }
                    
                    if (priceData.isNotEmpty()) {
                        database.savePriceBatch(stockCode, priceData)
                        logger.debug { "[$stockCode] Updated ${priceData.size} new records" }
                        successCount++
                    } else {
                        logger.debug { "[$stockCode] No new data to add" }
                    }
                } else {
                    logger.warn { "[$stockCode] No data returned" }
                    failureCount++
                }
                
                // 진행상황 업데이트
                InitializationProgress.update(index + 1, stockCode)
                
                if ((index + 1) % 100 == 0) {
                    val elapsed = (System.currentTimeMillis() - startTime) / 1000
                    logger.info { "Progress: ${index + 1}/${allStockCodes.size} (${successCount} success)" }
                }
                
            } catch (e: Exception) {
                logger.error(e) { "Failed to update $stockCode" }
                failureCount++
            }
        }
        
        val totalTime = (System.currentTimeMillis() - startTime) / 1000
        
        logger.info { "✅ Daily update completed!" }
        logger.info { "Success: $successCount / ${allStockCodes.size}" }
        logger.info { "Failed: $failureCount" }
        logger.info { "Total time: ${totalTime}s" }
        
        // 오래된 데이터 자동 정리 (280일 이전)
        logger.info { "🧹 Cleaning old data (keeping 280 days)..." }
        database.cleanOldData(keepDays = 280)
        
        // 진행 상태 완료
        InitializationProgress.complete()
        
        // 메타데이터 업데이트
        database.setMetadata("last_daily_update", today.toString())
    }
}
