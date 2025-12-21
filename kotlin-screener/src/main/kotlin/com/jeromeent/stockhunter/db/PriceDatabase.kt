package com.jeromeent.stockhunter.db

import kotlinx.serialization.Serializable
import mu.KotlinLogging
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.time.LocalDate
import java.time.LocalDateTime

private val logger = KotlinLogging.logger {}

/**
 * SQLite 기반 가격 데이터 데이터베이스
 * 
 * - 코스피/코스닥 전체 종목 (약 2,500개)
 * - 각 종목당 300일 데이터 (OHLCV)
 * - 총 약 750,000건, ~150MB
 */
class PriceDatabase(private val dbPath: String = "/root/.stockhunter/price_data.db") {
    
    private var connection: Connection? = null
    
    init {
        logger.info { "Initializing PriceDatabase at $dbPath" }
        connect()
        createTables()
    }
    
    private fun connect() {
        Class.forName("org.sqlite.JDBC")
        connection = DriverManager.getConnection("jdbc:sqlite:$dbPath")
        connection?.autoCommit = true
        logger.info { "Connected to SQLite database" }
    }
    
    private fun createTables() {
        connection?.createStatement()?.use { stmt ->
            // 일별 가격 테이블
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS daily_prices (
                    stock_code TEXT NOT NULL,
                    trade_date TEXT NOT NULL,
                    open_price REAL NOT NULL,
                    high_price REAL NOT NULL,
                    low_price REAL NOT NULL,
                    close_price REAL NOT NULL,
                    volume INTEGER NOT NULL,
                    created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                    updated_at TEXT DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (stock_code, trade_date)
                )
            """.trimIndent())
            
            // 인덱스 생성
            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_stock_date 
                ON daily_prices(stock_code, trade_date DESC)
            """.trimIndent())
            
            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_date 
                ON daily_prices(trade_date DESC)
            """.trimIndent())
            
            // 메타데이터 테이블
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS db_metadata (
                    key TEXT PRIMARY KEY,
                    value TEXT,
                    updated_at TEXT DEFAULT CURRENT_TIMESTAMP
                )
            """.trimIndent())
            
            // 종목 마스터 테이블 (전체 종목 리스트 캐싱)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS stock_master (
                    stock_code TEXT PRIMARY KEY,
                    market TEXT NOT NULL,
                    stock_name TEXT,
                    is_active INTEGER DEFAULT 1,
                    created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                    updated_at TEXT DEFAULT CURRENT_TIMESTAMP
                )
            """.trimIndent())
            
            // 종목 마스터 인덱스
            stmt.execute("""
                CREATE INDEX IF NOT EXISTS idx_market_active 
                ON stock_master(market, is_active)
            """.trimIndent())
            
            logger.info { "Database tables created/verified" }
        }
    }
    
    /**
     * 가격 데이터 저장 (배치)
     */
    fun savePriceBatch(stockCode: String, priceData: List<DailyPrice>) {
        if (priceData.isEmpty()) return
        
        val sql = """
            INSERT OR REPLACE INTO daily_prices 
            (stock_code, trade_date, open_price, high_price, low_price, close_price, volume, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()
        
        connection?.prepareStatement(sql)?.use { stmt ->
            priceData.forEach { price ->
                stmt.setString(1, stockCode)
                stmt.setString(2, price.date.toString())
                stmt.setDouble(3, price.open)
                stmt.setDouble(4, price.high)
                stmt.setDouble(5, price.low)
                stmt.setDouble(6, price.close)
                stmt.setLong(7, price.volume)
                stmt.setString(8, LocalDateTime.now().toString())
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
        
        logger.debug { "Saved ${priceData.size} price records for $stockCode" }
    }
    
    /**
     * 특정 종목의 가격 데이터 조회
     */
    fun getPrices(stockCode: String, days: Int = 300): List<DailyPrice> {
        val sql = """
            SELECT trade_date, open_price, high_price, low_price, close_price, volume
            FROM daily_prices
            WHERE stock_code = ?
            ORDER BY trade_date DESC
            LIMIT ?
        """.trimIndent()
        
        val result = mutableListOf<DailyPrice>()
        
        connection?.prepareStatement(sql)?.use { stmt ->
            stmt.setString(1, stockCode)
            stmt.setInt(2, days)
            
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    result.add(DailyPrice(
                        date = LocalDate.parse(rs.getString("trade_date")),
                        open = rs.getDouble("open_price"),
                        high = rs.getDouble("high_price"),
                        low = rs.getDouble("low_price"),
                        close = rs.getDouble("close_price"),
                        volume = rs.getLong("volume")
                    ))
                }
            }
        }
        
        return result
    }
    
    /**
     * 특정 종목의 최신 날짜 조회
     */
    fun getLatestDate(stockCode: String): LocalDate? {
        val sql = """
            SELECT MAX(trade_date) as latest_date
            FROM daily_prices
            WHERE stock_code = ?
        """.trimIndent()
        
        connection?.prepareStatement(sql)?.use { stmt ->
            stmt.setString(1, stockCode)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    val dateStr = rs.getString("latest_date")
                    if (dateStr != null) {
                        return LocalDate.parse(dateStr)
                    }
                }
            }
        }
        
        return null
    }
    
    /**
     * DB에 저장된 모든 종목코드 조회
     */
    fun getAllStockCodes(): List<String> {
        val sql = "SELECT DISTINCT stock_code FROM daily_prices ORDER BY stock_code"
        val result = mutableListOf<String>()
        
        connection?.createStatement()?.use { stmt ->
            stmt.executeQuery(sql).use { rs ->
                while (rs.next()) {
                    result.add(rs.getString("stock_code"))
                }
            }
        }
        
        return result
    }
    
    /**
     * 메타데이터 저장
     */
    fun setMetadata(key: String, value: String) {
        val sql = """
            INSERT OR REPLACE INTO db_metadata (key, value, updated_at)
            VALUES (?, ?, ?)
        """.trimIndent()
        
        connection?.prepareStatement(sql)?.use { stmt ->
            stmt.setString(1, key)
            stmt.setString(2, value)
            stmt.setString(3, LocalDateTime.now().toString())
            stmt.executeUpdate()
        }
    }
    
    /**
     * 메타데이터 조회
     */
    fun getMetadata(key: String): String? {
        val sql = "SELECT value FROM db_metadata WHERE key = ?"
        
        connection?.prepareStatement(sql)?.use { stmt ->
            stmt.setString(1, key)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    return rs.getString("value")
                }
            }
        }
        
        return null
    }
    
    /**
     * DB 통계 조회
     */
    fun getStatistics(): DatabaseStatistics {
        val stockCount = connection?.createStatement()?.executeQuery(
            "SELECT COUNT(DISTINCT stock_code) as cnt FROM daily_prices"
        )?.use { if (it.next()) it.getInt("cnt") else 0 } ?: 0
        
        val recordCount = connection?.createStatement()?.executeQuery(
            "SELECT COUNT(*) as cnt FROM daily_prices"
        )?.use { if (it.next()) it.getInt("cnt") else 0 } ?: 0
        
        val oldestDate = connection?.createStatement()?.executeQuery(
            "SELECT MIN(trade_date) as oldest FROM daily_prices"
        )?.use { 
            if (it.next()) {
                val dateStr = it.getString("oldest")
                if (dateStr != null) LocalDate.parse(dateStr) else null
            } else null
        }
        
        val newestDate = connection?.createStatement()?.executeQuery(
            "SELECT MAX(trade_date) as newest FROM daily_prices"
        )?.use { 
            if (it.next()) {
                val dateStr = it.getString("newest")
                if (dateStr != null) LocalDate.parse(dateStr) else null
            } else null
        }
        
        return DatabaseStatistics(
            totalStocks = stockCount,
            totalRecords = recordCount,
            oldestDate = oldestDate,
            newestDate = newestDate
        )
    }
    
    /**
     * 오래된 데이터 정리 (300일 이전)
     */
    fun cleanOldData(keepDays: Int = 300) {
        val cutoffDate = LocalDate.now().minusDays(keepDays.toLong())
        
        val sql = "DELETE FROM daily_prices WHERE trade_date < ?"
        
        connection?.prepareStatement(sql)?.use { stmt ->
            stmt.setString(1, cutoffDate.toString())
            val deleted = stmt.executeUpdate()
            logger.info { "Cleaned $deleted old records (before $cutoffDate)" }
        }
    }
    
    // ========== 종목 마스터 관리 ==========
    
    /**
     * 종목 마스터 전체 갱신
     * 
     * @param stocks 종목 코드와 시장 정보 맵 (종목코드 -> 시장)
     */
    fun refreshStockMaster(stocks: Map<String, String>) {
        connection?.prepareStatement("BEGIN TRANSACTION")?.execute()
        
        try {
            val insertSql = """
                INSERT OR REPLACE INTO stock_master (stock_code, market, is_active, updated_at)
                VALUES (?, ?, 1, ?)
            """.trimIndent()
            
            connection?.prepareStatement(insertSql)?.use { stmt ->
                stocks.forEach { (code, market) ->
                    stmt.setString(1, code)
                    stmt.setString(2, market)
                    stmt.setString(3, LocalDateTime.now().toString())
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
            
            // 갱신 시간 저장
            setMetadata("stock_master_updated_at", LocalDateTime.now().toString())
            
            connection?.prepareStatement("COMMIT")?.execute()
            
            logger.info { "✅ Stock master refreshed: ${stocks.size} stocks" }
        } catch (e: Exception) {
            connection?.prepareStatement("ROLLBACK")?.execute()
            logger.error(e) { "Failed to refresh stock master" }
            throw e
        }
    }
    
    /**
     * 캐시된 종목 리스트 조회
     * 
     * @return 종목 코드 리스트
     */
    fun getCachedStockCodes(): List<String> {
        val sql = "SELECT stock_code FROM stock_master WHERE is_active = 1 ORDER BY stock_code"
        
        val result = mutableListOf<String>()
        
        connection?.createStatement()?.executeQuery(sql)?.use { rs ->
            while (rs.next()) {
                result.add(rs.getString("stock_code"))
            }
        }
        
        return result
    }
    
    /**
     * 종목 마스터 갱신 필요 여부 확인
     * 
     * @param maxAgeDays 최대 유효 기간 (기본 7일)
     * @return true면 갱신 필요
     */
    fun needsStockMasterRefresh(maxAgeDays: Int = 7): Boolean {
        val lastUpdated = getMetadata("stock_master_updated_at")
        
        if (lastUpdated == null) {
            logger.info { "Stock master never updated, refresh needed" }
            return true
        }
        
        return try {
            val lastUpdate = LocalDateTime.parse(lastUpdated)
            val daysSinceUpdate = java.time.Duration.between(lastUpdate, LocalDateTime.now()).toDays()
            
            val needsRefresh = daysSinceUpdate >= maxAgeDays
            
            if (needsRefresh) {
                logger.info { "Stock master is $daysSinceUpdate days old, refresh needed" }
            }
            
            needsRefresh
        } catch (e: Exception) {
            logger.warn(e) { "Failed to parse last update time" }
            true
        }
    }
    
    /**
     * 종목 마스터 통계
     */
    fun getStockMasterStats(): StockMasterStats {
        val totalCount = connection?.createStatement()?.executeQuery(
            "SELECT COUNT(*) as cnt FROM stock_master WHERE is_active = 1"
        )?.use { if (it.next()) it.getInt("cnt") else 0 } ?: 0
        
        val kospiCount = connection?.createStatement()?.executeQuery(
            "SELECT COUNT(*) as cnt FROM stock_master WHERE is_active = 1 AND market = 'KOSPI'"
        )?.use { if (it.next()) it.getInt("cnt") else 0 } ?: 0
        
        val kosdaqCount = connection?.createStatement()?.executeQuery(
            "SELECT COUNT(*) as cnt FROM stock_master WHERE is_active = 1 AND market = 'KOSDAQ'"
        )?.use { if (it.next()) it.getInt("cnt") else 0 } ?: 0
        
        val lastUpdated = getMetadata("stock_master_updated_at")
        
        return StockMasterStats(
            totalStocks = totalCount,
            kospiStocks = kospiCount,
            kosdaqStocks = kosdaqCount,
            lastUpdated = lastUpdated
        )
    }
    
    fun close() {
        connection?.close()
        logger.info { "Database connection closed" }
    }
}

/**
 * 일별 가격 데이터
 */
data class DailyPrice(
    val date: LocalDate,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long
)

/**
 * 종목 마스터 통계
 */
@Serializable
data class StockMasterStats(
    val totalStocks: Int,
    val kospiStocks: Int,
    val kosdaqStocks: Int,
    val lastUpdated: String?
)

/**
 * DB 통계
 */
data class DatabaseStatistics(
    val totalStocks: Int,
    val totalRecords: Int,
    val oldestDate: LocalDate?,
    val newestDate: LocalDate?
)
