package com.keycheck

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** 테스트에서 시간을 직접 움직이는 Clock */
class MutableClock(private var now: Instant = Instant.parse("2026-11-01T00:00:00Z")) : Clock() {
    fun advance(duration: Duration) {
        now = now.plus(duration)
    }

    override fun instant(): Instant = now
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
}
