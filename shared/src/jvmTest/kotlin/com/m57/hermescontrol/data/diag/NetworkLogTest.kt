package com.m57.hermescontrol.data.diag

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the two properties the diagnostics screen relies on: the buffer is
 * bounded, and the newest event is last (so the list reads top-down in time
 * order, which is how it is rendered).
 */
class NetworkLogTest {
    @BeforeTest
    fun clear() {
        NetworkLog.clear()
    }

    @Test
    fun `records events in order with dense sequence numbers`() {
        repeat(3) { i ->
            NetworkLog.record("GET", "https://example.test/api/status", "HTTP 401", NetworkLog.start())
        }

        val events = NetworkLog.events.value
        assertEquals(3, events.size)
        assertEquals(listOf(1L, 2L, 3L), events.map { it.seq })
        assertTrue(events.all { it.durationMillis >= 0 })
    }

    @Test
    fun `keeps only the newest capacity events`() {
        repeat(NetworkLog.CAPACITY + 5) {
            NetworkLog.record("POST", "https://example.test/api/ws", "HTTP 200", NetworkLog.start())
        }

        val events = NetworkLog.events.value
        assertEquals(NetworkLog.CAPACITY, events.size)
        // Re-numbered after trimming, so the last event is the capacity-th.
        assertEquals(NetworkLog.CAPACITY.toLong(), events.last().seq)
    }

    @Test
    fun `transport failures are not http outcomes`() {
        NetworkLog.record("GET", "https://example.test/api/status", "HTTP 500", NetworkLog.start())
        NetworkLog.record("GET", "https://example.test/api/status", "RequestTimeoutException", NetworkLog.start())

        val events = NetworkLog.events.value
        assertFalse(events[0].isTransportFailure)
        assertTrue(events[1].isTransportFailure)
    }
}
