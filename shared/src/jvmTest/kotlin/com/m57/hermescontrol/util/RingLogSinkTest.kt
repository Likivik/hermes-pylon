package com.m57.hermescontrol.util

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the properties the diagnostics screen and the share bundle rely on:
 * a bounded ring, dense sequence numbers after trimming, a severity threshold
 * that actually filters, and redaction applied on the way in.
 */
class RingLogSinkTest {
    @BeforeTest
    fun reset() {
        HermesLog.ring.clear()
        HermesLog.clearInstalledSinks()
    }

    @Test
    fun `keeps only the newest records and renumbers densely`() {
        repeat(HermesLog.ring.capacity + 5) {
            HermesLog.record(LogSeverity.INFO, "test", "line $it")
        }

        val records = HermesLog.records.value
        assertEquals(HermesLog.ring.capacity, records.size)
        assertEquals(HermesLog.ring.capacity.toLong(), records.last().seq)
        assertEquals((1L..records.size.toLong()).toList(), records.map { it.seq })
    }

    @Test
    fun `a sink threshold filters below it`() {
        val infoOnly = RingLogSink(capacity = 10, threshold = LogSeverity.WARN)
        infoOnly.record(record(LogSeverity.DEBUG))
        infoOnly.record(record(LogSeverity.WARN))
        infoOnly.record(record(LogSeverity.ERROR))

        assertEquals(listOf(LogSeverity.WARN, LogSeverity.ERROR), infoOnly.records.value.map { it.severity })
    }

    @Test
    fun `secret attributes are masked on the way in`() {
        HermesLog.record(
            severity = LogSeverity.INFO,
            component = "net",
            message = "POST /api/auth",
            attributes = mapOf("authorization" to "Bearer super-secret", "outcome" to "HTTP 200"),
        )

        val last = HermesLog.records.value.last()
        assertEquals("<redacted len=19>", last.attributes["authorization"])
        assertEquals("HTTP 200", last.attributes["outcome"])
        assertTrue(last.attributes.getValue("authorization").none { it == 's' })
    }

    @Test
    fun `error type is carried without a stack trace`() {
        HermesLog.record(LogSeverity.WARN, "net", "GET /api/status", error = IllegalStateException("boom"))

        assertEquals("IllegalStateException", HermesLog.records.value.last().errorType)
    }

    @Test
    fun `installed sinks receive the same record as the ring`() {
        val captured = mutableListOf<LogRecord>()
        val sink =
            object : LogSink {
                override val isDebug: Boolean = true

                override fun d(
                    tag: String,
                    message: String,
                ) = Unit

                override fun i(
                    tag: String,
                    message: String,
                ) = Unit

                override fun w(
                    tag: String,
                    message: String,
                ) = Unit

                override fun e(
                    tag: String,
                    message: String,
                ) = Unit

                override fun record(record: LogRecord) {
                    captured += record
                }
            }
        HermesLog.install(sink)
        HermesLog.record(LogSeverity.INFO, "test", "hello")

        assertEquals(1, captured.size)
        assertEquals(HermesLog.records.value.last().message, captured.single().message)
    }

    private fun record(severity: LogSeverity) =
        LogRecord(seq = 0, atMillis = 0, severity = severity, component = "test", message = "m")
}
