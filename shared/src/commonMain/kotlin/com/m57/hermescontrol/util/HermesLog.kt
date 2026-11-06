package com.m57.hermescontrol.util

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.mutableLoggerConfigInit
import co.touchlab.kermit.platformLogWriter
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

    /**
     * The logging engine. Kermit owns the per-platform writers (logcat on
     * Android, `os_log` on iOS, `console` on wasm, stdout on the JVM), so this
     * module no longer ships a hand-rolled sink per target.
     *
     * Severity policy lives here, not at the call sites: debug builds keep
     * Debug and above, release keeps Info and above. A verbose line therefore
     * costs nothing in release without a single `if (BuildConfig.DEBUG)` in the
     * logging code.
     */
    @Volatile
    private var engine: Logger =
        Logger(
            mutableLoggerConfigInit(platformLogWriter(), minSeverity = Severity.Info),
        )

    /** Called once at start-up with the build's own debug flag. */
    fun setDebugLogging(enabled: Boolean) {
        engine =
            Logger(
                mutableLoggerConfigInit(
                    platformLogWriter(),
                    minSeverity = if (enabled) Severity.Verbose else Severity.Info,
                ),
            )
    }

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
        // One line per record, at the engine's severity gate, through the
        // platform writer. `record` already redacted and capped the attributes.
        val line = record.render()
        val tag = record.component
        runCatching {
            // Named arguments: Kermit's positional order differs between the
            // `log()` overloads, and this is the one place that matters.
            when (record.severity) {
                LogSeverity.VERBOSE -> engine.v(message = { line }, tag = tag)
                LogSeverity.DEBUG -> engine.d(message = { line }, tag = tag)
                LogSeverity.INFO -> engine.i(message = { line }, tag = tag)
                LogSeverity.WARN -> engine.w(message = { line }, tag = tag)
                LogSeverity.ERROR -> engine.e(message = { line }, tag = tag)
            }
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
