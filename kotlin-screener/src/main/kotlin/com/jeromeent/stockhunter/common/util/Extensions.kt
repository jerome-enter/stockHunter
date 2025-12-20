package com.jeromeent.stockhunter.common.util

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Kotlin 확장 함수
 */

/**
 * Double을 퍼센트 비율로 계산
 */
fun Double.toPercentage(base: Double): Double {
    return (this / base) * 100.0
}

/**
 * Double을 소수점 n자리로 반올림
 */
fun Double.roundTo(decimals: Int): Double {
    val multiplier = Math.pow(10.0, decimals.toDouble())
    return kotlin.math.round(this * multiplier) / multiplier
}

/**
 * 현재 타임스탬프를 ISO 형식으로 반환
 */
fun now(): String {
    return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
}

/**
 * 리스트를 안전하게 청크로 분할
 */
fun <T> List<T>.chunkedSafe(size: Int): List<List<T>> {
    if (size <= 0) return listOf(this)
    return this.chunked(size)
}

/**
 * String을 Double로 안전하게 변환
 */
fun String.toDoubleOrDefault(default: Double = 0.0): Double {
    return this.toDoubleOrNull() ?: default
}

/**
 * String을 Long으로 안전하게 변환
 */
fun String.toLongOrDefault(default: Long = 0L): Long {
    return this.toLongOrNull() ?: default
}

/**
 * 조건부 실행
 */
inline fun <T> T.runIf(condition: Boolean, block: T.() -> T): T {
    return if (condition) block() else this
}
