package com.m57.hermescontrol.util

import co.touchlab.kermit.BaseLogger
import co.touchlab.kermit.ExperimentalKermitApi
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.TestLogWriter
import co.touchlab.kermit.mutableLoggerConfigInit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Proves records reach the logging engine, at the right severity, through the
 * configured gate — on every CI run, instead of by loading the app and reading a
 * browser console.
 *
 * The engine is swapped for one whose config carries Kermit's [TestLogWriter], so
 * the assertion covers the same dispatch path production uses, not a reimplementation
 * of our own facade.
 */
@OptIn(ExperimentalKermitApi::class)
class HermesLogEngineTest {
    private lateinit var writer: TestLogWriter
    private lateinit var original: BaseLogger

    @BeforeTest
    fun installCapturingEngine() {
        original = HermesLog.engine
        writer = TestLogWriter(Severity.Verbose)
        HermesLog.engine =
            Logger(
                mutableLoggerConfigInit(writer, minSeverity = Severity.Verbose),
            )
    }

    @AfterTest
    fun restoreEngine() {
        HermesLog.engine = original
    }

    private fun captured(): List<String> = writer.logs.map { it.message }

    @Test
    fun everyLevelReachesTheEngine() {
        HermesLog.d("t", "debug-line")
        HermesLog.i("t", "info-line")
        HermesLog.w("t", "warn-line")
        HermesLog.e("t", "error-line")

        val messages = captured()
        // render() prefixes "component SEVERITY ", so match on suffix.
        for (line in listOf("debug-line", "info-line", "warn-line", "error-line")) {
            assertTrue(messages.any { it.endsWith(line) }, "engine did not receive '$line'; got $messages")
        }
    }

    @Test
    fun theReleaseGateDropsDebugAndKeepsInfo() {
        HermesLog.setDebugLogging(false)
        HermesLog.d("t", "quiet-line")
        HermesLog.i("t", "loud-line")

        val messages = captured()
        assertTrue(messages.none { it.endsWith("quiet-line") }, "debug survived the Info gate: $messages")
        assertTrue(messages.any { it.endsWith("loud-line") }, "info was dropped by the Info gate: $messages")
    }

    @Test
    fun theRingKeepsEveryRecordRegardlessOfTheGate() {
        HermesLog.setDebugLogging(false)
        val before = HermesLog.records.value.size
        HermesLog.d("t", "ring-only")
        val added = HermesLog.records.value.size - before
        assertEquals(1, added, "the ring must record what the engine filters")
    }
}
