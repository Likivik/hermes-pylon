package com.m57.hermescontrol.e2e

import java.io.BufferedReader

/** Reads recent device logcat from the instrumentation process. */
object DeviceLog {
    fun recent(): String = try {
        val p = ProcessBuilder("logcat", "-d", "-t", "1500",
            "AndroidRuntime:E", "System.err:W", "ActivityTaskManager:W", "DataStore:E", "*:F")
            .redirectErrorStream(true).start()
        p.inputStream.bufferedReader().readText().takeLast(6000)
    } catch (t: Throwable) {
        "logcat unavailable: $t"
    }

    /** Run [block]; on failure rethrow with the recent device log embedded. */
    inline fun withEvidence(tag: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            throw IllegalStateException("[$tag] FAILED — device log:\n${recent()}", t)
        }
    }
}
