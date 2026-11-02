package com.keycheck.crawl

import com.keycheck.MutableClock
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RequestGateTest {

    private val clock = MutableClock()
    private val sleeps = mutableListOf<Duration>()
    private val gate = RequestGate(Duration.ofSeconds(2), clock) { duration ->
        sleeps.add(duration)
        clock.advance(duration)
    }

    @Test
    fun `tryAcquire는 간격이 찼을 때만 통과한다`() {
        assertTrue(gate.tryAcquire())

        clock.advance(Duration.ofMillis(1999))
        assertFalse(gate.tryAcquire())

        clock.advance(Duration.ofMillis(1))
        assertTrue(gate.tryAcquire())
    }

    @Test
    fun `acquire는 남은 시간만큼 기다린 뒤 통과한다`() {
        gate.acquire()
        assertEquals(emptyList(), sleeps)

        clock.advance(Duration.ofMillis(500))
        gate.acquire()
        assertEquals(listOf(Duration.ofMillis(1500)), sleeps)
    }

    @Test
    fun `acquire가 통과하면 그 뒤 tryAcquire는 막힌다`() {
        gate.acquire()
        assertFalse(gate.tryAcquire())
    }
}
