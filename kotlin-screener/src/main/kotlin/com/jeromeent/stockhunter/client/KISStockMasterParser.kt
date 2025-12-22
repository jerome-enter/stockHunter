package com.jeromeent.stockhunter.client

/**
 * 한국투자증권 종목마스터 파일 파서
 * 
 * 고정 길이 포맷:
 * - 위치 0-8: 단축코드 (9자, 앞 6자리가 종목코드)
 * - 위치 9-20: 표준코드 (12자, ISIN)
 * - 위치 21-60: 한글종목명 (40자)
 * - 위치 61-62: 증권그룹구분코드 (2자)
 */
object KISStockMasterParser {
    
    /**
     * 종목마스터 파일 파싱
     * 
     * 고정 길이 포맷:
     * - 위치 0-8: 단축코드 (9자)
     * - 위치 9-20: 표준코드 (12자)
     * - 위치 21-60: 한글종목명 (40자)
     * - 위치 61-62: 증권그룹구분코드 (2자)
     * 
     * @param content 파일 내용 (전체 텍스트)
     * @param market 시장 구분 (KOSPI/KOSDAQ)
     * @return (종목코드, 종목명, 시장) 트리플 리스트
     */
    fun parseStockMasterFile(content: String, market: String): List<Triple<String, String, String>> {
        return content.lines()
            .filter { it.length >= 61 }  // 최소 61자 이상
            .mapNotNull { line ->
                try {
                    // 위치 0-8: 단축코드 (종목코드)
                    val stockCode = line.substring(0, 9).trim()
                    
                    // 위치 21-60: 한글종목명
                    val stockName = if (line.length >= 61) {
                        line.substring(21, 61).trim()
                    } else {
                        ""
                    }
                    
                    // 숫자 6자리인지 검증 (앞 6자리만)
                    if (stockCode.length >= 6 && stockCode.substring(0, 6).matches(Regex("\\d{6}"))) {
                        val code = stockCode.substring(0, 6)
                        Triple(code, stockName, market)
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    null
                }
            }
            .distinct()  // 중복 제거
    }
}
