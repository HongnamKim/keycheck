package com.keycheck.crawl

import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * 모든 geekhack 요청이 공유하는 간격 제한 (설계 4.3).
 * - 사용자 조회: tryAcquire() — 간격이 안 찼으면 즉시 false
 * - 정기 수집: acquire() — 간격이 찰 때까지 기다림
 */
class RequestGate(
    private val minInterval: Duration,
    private val clock: Clock,
    private val sleeper: (Duration) -> Unit = { Thread.sleep(it) },
) {
    private var lastAcquiredAt: Instant? = null

    @Synchronized
    fun tryAcquire(): Boolean = remainingWait() == Duration.ZERO && mark()

    fun acquire() {
        while (true) {
            val wait = synchronized(this) {
                val remaining = remainingWait()
                if (remaining == Duration.ZERO) mark()
                remaining
            }
            if (wait == Duration.ZERO) return
            sleeper(wait)
        }
    }

    private fun remainingWait(): Duration {
        val last = lastAcquiredAt ?: return Duration.ZERO
        val elapsed = Duration.between(last, clock.instant())
        return if (elapsed >= minInterval) Duration.ZERO else minInterval - elapsed
    }

    private fun mark(): Boolean {
        lastAcquiredAt = clock.instant()
        return true
    }
}
