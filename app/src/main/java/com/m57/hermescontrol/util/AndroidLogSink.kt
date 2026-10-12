package com.m57.hermescontrol.util

import android.util.Log
import com.m57.hermescontrol.BuildConfig

/**
 * `android.util.Log`-backed sink for [HermesLog].
 *
 * Carries the app's `BuildConfig.DEBUG` so shared code can gate the call sites
 * that stay silent in release builds.
 */
object AndroidLogSink : LogSink {
    override val isDebug: Boolean get() = BuildConfig.DEBUG

    override fun d(
        tag: String,
        message: String,
    ) {
        Log.d(tag, message)
    }

    override fun i(
        tag: String,
        message: String,
    ) {
        Log.i(tag, message)
    }

    override fun w(
        tag: String,
        message: String,
    ) {
        Log.w(tag, message)
    }

    override fun e(
        tag: String,
        message: String,
    ) {
        Log.e(tag, message)
    }
}
