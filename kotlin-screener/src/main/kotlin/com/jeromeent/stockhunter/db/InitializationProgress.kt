package com.jeromeent.stockhunter.db

import kotlinx.serialization.Serializable
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * 간단한 초기화 진행 상태 추적
 * 
 * 전역 싱글톤으로 현재 진행 상황 저장
 */
object InitializationProgress {
    
    private val isRunning = java.util.concurrent.atomic.AtomicBoolean(false)
    private val currentCount = AtomicInteger(0)
    private val totalCount = AtomicInteger(0)
    private val currentStock = java.util.concurrent.atomic.AtomicReference<String>("")
    private val startTime = AtomicLong(0)
    
    /**
     * 초기화 시작
     */
    fun start(total: Int) {
        isRunning.set(true)
        currentCount.set(0)
        totalCount.set(total)
        currentStock.set("")
        startTime.set(System.currentTimeMillis())
    }
    
    /**
     * 진행 업데이트
     */
    fun update(current: Int, stock: String) {
        currentCount.set(current)
        currentStock.set(stock)
    }
    
    /**
     * 완료
     */
    fun complete() {
        isRunning.set(false)
        currentStock.set("Completed")
    }
    
    /**
     * 현재 상태 조회
     */
    fun getStatus(): ProgressStatus {
        val current = currentCount.get()
        val total = totalCount.get()
        val elapsed = (System.currentTimeMillis() - startTime.get()) / 1000
        
        val percentage = if (total > 0) (current * 100) / total else 0
        val estimatedTotal = if (current > 0) (elapsed * total) / current else 0
        val remaining = estimatedTotal - elapsed
        
        return ProgressStatus(
            isRunning = isRunning.get(),
            currentCount = current,
            totalCount = total,
            currentStock = currentStock.get(),
            percentage = percentage,
            elapsedSeconds = elapsed,
            remainingSeconds = if (remaining > 0) remaining else 0
        )
    }
    
    /**
     * 리셋
     */
    fun reset() {
        isRunning.set(false)
        currentCount.set(0)
        totalCount.set(0)
        currentStock.set("")
        startTime.set(0)
    }
}

@Serializable
data class ProgressStatus(
    val isRunning: Boolean,
    val currentCount: Int,
    val totalCount: Int,
    val currentStock: String,
    val percentage: Int,
    val elapsedSeconds: Long,
    val remainingSeconds: Long
)
