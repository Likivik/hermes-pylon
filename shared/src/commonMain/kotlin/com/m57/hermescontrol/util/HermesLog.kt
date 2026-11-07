package com.m57.hermescontrol.util

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
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
     * The engine. Defaults to Kermit's singleton logger, which already writes to
     * the platform sink (logcat, `console`, stdout). `internal` so a test can
     * swap in a logger whose config carries a `TestLogWriter` — that is the whole
     * reason this is a facade rather than direct Kermit calls.
     */
    internal var engine: co.touchlab.kermit.BaseLogger = Logger

    /**
     * Severity gate, set once per entry point: Verbose in debug, Info in release.
     *
     * Applied to the engine's own config. If the config cannot be mutated the
     * failure is recorded in the ring rather than dropped — a gate that silently
     * does nothing is how "logging is broken" stays invisible.
     */
    fun setDebugLogging(enabled: Boolean) {
        val target = if (enabled) Severity.Verbose else Severity.Info
        try {
            engine.mutableConfig.minSeverity = target
        } catch (notMutable: Throwable) {
            ring.record(
                LogRecord(
                    seq = 0,
                    atMillis = nowMillis(),
                    severity = LogSeverity.ERROR,
                    component = "logging",
                    message = "severity gate not applied: ${notMutable::class.simpleName}",
                ),
            )
        }
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
        val line = record.render()
        val tag = record.component
        try {
            // Named args: Kermit's positional order varies between overloads and
            // the message parameter is a lazy lambda.
            // Positional arguments, in Kermit's actual order: (message, throwable,
            // tag). The named form is ambiguous between the eager and lazy
            // overloads, and the line is already rendered, so lazy buys nothing.
            // BaseLogger.log(severity, tag, throwable, message). Verified by test:
            // passing the message second puts the tag in the message slot.
            engine.log(severity.toKermit(), tag, null, line)
        } catch (engineFailure: Throwable) {
            // An engine failure must not be silent — that is the one logging bug
            // nobody finds later. The ring cannot recurse through the engine, so
            // the diagnostics screen shows it.
            ring.record(
                LogRecord(
                    seq = 0,
                    atMillis = nowMillis(),
                    severity = LogSeverity.ERROR,
                    component = "logging",
                    message = "engine failure: ${engineFailure::class.simpleName}",
                ),
            )
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

    private fun LogSeverity.toKermit(): Severity =
        when (this) {
            LogSeverity.VERBOSE -> Severity.Verbose
            LogSeverity.DEBUG -> Severity.Debug
            LogSeverity.INFO -> Severity.Info
            LogSeverity.WARN -> Severity.Warn
            LogSeverity.ERROR -> Severity.Error
        }
}
