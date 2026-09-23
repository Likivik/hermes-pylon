package com.m57.hermescontrol.e2e

import androidx.test.platform.app.InstrumentationRegistry

/** Reads recent device logcat via UiAutomation (works under instrumentation). */
object DeviceLog {
    fun recent(): String = try {
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        val pfd = ui.executeShellCommand("logcat -d -t 1500 AndroidRuntime:E System.err:W ActivityTaskManager:W *:F")
        val text = pfd.fileDescriptor.let { fd ->
            java.io.FileDescriptor().let { _ ->
                java.io.FileInputStream(fd).bufferedReader().readText()
            }
        }
        pfd.close()
        text.takeLast(6000)
    } catch (t: Throwable) {
        "logcat unavailable: $t"
    }

    /** Run [block]; on failure rethrow with device log + optional state dump. */
    inline fun withEvidence(
        tag: String,
        noinline extra: (() -> String)? = null,
        block: () -> Unit,
    ) {
        try {
            block()
        } catch (t: Throwable) {
            val detail = try {
                extraDetail(extra)
            } catch (_: Throwable) {
                "extra unavailable"
            }
            throw IllegalStateException(
                "[$tag] FAILED — device log:\n${recent()}\nBROADCAST/STATE:\n$detail",
                t,
            )
        }
    }

    private fun extraDetail(extra: (() -> String)?): String =
        extra?.invoke() ?: ""
}
