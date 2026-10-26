package com.m57.hermescontrol.util

/**
 * Web console sink.
 *
 * Deliberately uses `println`: Kotlin/Wasm maps it onto `console.log`, whereas the
 * DOM `console` binding does not resolve from this module's configuration
 * (`Window.console` is not part of the DOM surface this module sees). Severity is
 * carried in the rendered line, so nothing is lost by not choosing a specific
 * console method.
 */
class ConsoleLogSink(
    override val isDebug: Boolean = true,
) : LogSink {
    override fun record(record: LogRecord) {
        println(record.render())
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
    ) = println("WARN $tag: $message")

    override fun e(
        tag: String,
        message: String,
    ) = println("ERROR $tag: $message")
}
