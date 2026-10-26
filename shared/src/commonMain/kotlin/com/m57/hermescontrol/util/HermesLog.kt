package com.m57.hermescontrol.util

import kotlin.concurrent.Volatile

/**
 * Where `:shared` code sends its diagnostics.
 *
 * Installed by the app rather than declared `expect`/`actual` like
 * [nowMillis]: the guarded call sites need the app's `BuildConfig.DEBUG`, which
 * a library module cannot see, and a console target wants its own sink anyway.
 */
interface LogSink {
    /** True for debug builds; gates the call sites that stay silent in release. */
    val isDebug: Boolean

    fun d(
        tag: String,
        message: String,
    )

    fun i(
        tag: String,
        message: String,
    )

    fun w(
        tag: String,
        message: String,
    )

    fun e(
        tag: String,
        message: String,
    )

    /**
     * Variants that carry a throwable. Default implementations fold it into the
     * message so a sink does not have to exist for every target, while the
     * Android sink still forwards a real stack trace to logcat.
     */
    fun w(
        tag: String,
        message: String,
        throwable: Throwable?,
    ) = w(tag, if (throwable == null) message else "$message: ${throwable.message}")

    fun e(
        tag: String,
        message: String,
        throwable: Throwable?,
    ) = e(tag, if (throwable == null) message else "$message: ${throwable.message}")

    /**
     * Structured entry point.
     *
     * The default maps a record onto the legacy severity methods, so a sink that
     * only knows about `d/i/w/e` keeps working unchanged — adding structure must
     * not force every platform sink to be rewritten in the same commit.
     */
    fun record(record: LogRecord) {
        val line = record.render()
        when (record.severity) {
            LogSeverity.VERBOSE, LogSeverity.DEBUG -> d(record.component, line)
            LogSeverity.INFO -> i(record.component, line)
            LogSeverity.WARN -> w(record.component, line)
            LogSeverity.ERROR -> e(record.component, line)
        }
    }
}

/**
 * Log facade for shared code.
 *
 * Defaults to a no-op until the app installs a sink — that default is what lets
 * `:shared` stay free of `android.util.Log` while production logging lands in
 * exactly the same place it did before.
 */
object HermesLog : LogSink {
    /** Always-on in-memory history; the diagnostics screen reads this. */
    val ring: RingLogSink = RingLogSink()

    @Volatile
    private var installed: List<LogSink> = emptyList()

    /** Records from the always-on ring, newest last. */
    val records get() = ring.records

    override val isDebug: Boolean
        get() = ring.isDebug || installed.any { it.isDebug }

    /** Adds a platform sink; intended for app start-up. */
    fun install(newSink: LogSink) {
        if (installed.none { it === newSink }) {
            installed = installed + newSink
        }
    }

    /** Test hook: drop the installed platform sinks, keep the ring. */
    fun clearInstalledSinks() {
        installed = emptyList()
    }

    /**
     * Builds and dispatches one record.
     *
     * Redaction and the attribute cap happen here, once, so no sink can forget
     * them and no call site can bypass them.
     */
    fun record(
        severity: LogSeverity,
        component: String,
        message: String,
        attributes: Map<String, String> = emptyMap(),
        error: Throwable? = null,
        requestId: String? = null,
    ) {
        val record =
            LogRecord(
                seq = 0,
                atMillis = nowMillis(),
                severity = severity,
                component = component,
                message = message,
                attributes = LogRedaction.attributes(attributes),
                errorType = error?.let { it::class.simpleName },
                requestId = requestId,
            )
        ring.record(record)
        installed.forEach { sink ->
            runCatching { sink.record(record) }
        }
    }

    override fun d(
        tag: String,
        message: String,
    ) = record(LogSeverity.DEBUG, tag, message)

    override fun i(
        tag: String,
        message: String,
    ) = record(LogSeverity.INFO, tag, message)

    override fun w(
        tag: String,
        message: String,
    ) = record(LogSeverity.WARN, tag, message)

    override fun e(
        tag: String,
        message: String,
    ) = record(LogSeverity.ERROR, tag, message)
}
