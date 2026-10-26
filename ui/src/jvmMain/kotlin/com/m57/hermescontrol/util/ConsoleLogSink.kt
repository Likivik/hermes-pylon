package com.m57.hermescontrol.util

/**
 * Desktop console sink. `stderr` for warnings and errors so a launched app's
 * output can be split from normal chatter, `stdout` for the rest.
 */
class ConsoleLogSink(
    override val isDebug: Boolean = true,
) : LogSink {
    override fun record(record: LogRecord) {
        val line = record.render()
        when (record.severity) {
            LogSeverity.ERROR, LogSeverity.WARN -> System.err.println(line)
            else -> println(line)
        }
    }

    override fun d(
        tag: String,
        message: String,
    ) = println("$tag: $message")

    override fun i(
        tag: String,
        message: String,
    ) = println("$tag: $message")

    override fun w(
        tag: String,
        message: String,
    ) = System.err.println("$tag: $message")

    override fun e(
        tag: String,
        message: String,
    ) = System.err.println("$tag: $message")
}
