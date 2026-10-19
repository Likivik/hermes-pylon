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
}

/**
 * Log facade for shared code.
 *
 * Defaults to a no-op until the app installs a sink — that default is what lets
 * `:shared` stay free of `android.util.Log` while production logging lands in
 * exactly the same place it did before.
 */
object HermesLog : LogSink {
    private object NoOp : LogSink {
        override val isDebug: Boolean = false

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
    }

    @Volatile
    private var sink: LogSink = NoOp

    override val isDebug: Boolean get() = sink.isDebug

    fun install(newSink: LogSink) {
        sink = newSink
    }

    override fun d(
        tag: String,
        message: String,
    ) = sink.d(tag, message)

    override fun i(
        tag: String,
        message: String,
    ) = sink.i(tag, message)

    override fun w(
        tag: String,
        message: String,
    ) = sink.w(tag, message)

    override fun e(
        tag: String,
        message: String,
    ) = sink.e(tag, message)
}
