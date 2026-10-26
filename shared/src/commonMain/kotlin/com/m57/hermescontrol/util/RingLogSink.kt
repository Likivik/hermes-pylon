package com.m57.hermescontrol.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The always-present in-memory log: bounded, filterable, and the thing the
 * diagnostics screen reads.
 *
 * Installed by default in [HermesLog] rather than by the app, because the one
 * moment the app cannot be trusted to install its own logging is the moment
 * logging matters: a failed start-up, a wasm page whose entry code threw, a
 * target nobody wired yet.
 */
class RingLogSink(
    val capacity: Int = DEFAULT_CAPACITY,
    private val threshold: LogSeverity = LogSeverity.DEBUG,
) : LogSink {
    private val _records = MutableStateFlow<List<LogRecord>>(emptyList())

    /** Newest last; sequence numbers are dense and monotonic after trimming. */
    val records: StateFlow<List<LogRecord>> = _records.asStateFlow()

    override val isDebug: Boolean get() = threshold == LogSeverity.DEBUG || threshold == LogSeverity.VERBOSE

    override fun record(record: LogRecord) {
        if (!record.severity.passes(threshold)) return
        _records.update { current ->
            val next = current + record
            val trimmed = if (next.size > capacity) next.takeLast(capacity) else next
            trimmed.mapIndexed { index, r -> r.copy(seq = index + 1L) }
        }
    }

    fun clear() {
        _records.value = emptyList()
    }

    /** Convenience for the diagnostics screen and tests. */
    fun ofComponent(component: String): List<LogRecord> = _records.value.filter { it.component == component }

    override fun d(
        tag: String,
        message: String,
    ) = Unit // route through record(); the legacy methods are for platform sinks only

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

    companion object {
        const val DEFAULT_CAPACITY: Int = 200
    }
}
