package com.m57.hermescontrol.util

import kotlin.js.ExperimentalWasmJsInterop

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("() => Date.now()")
private external fun jsNowMillis(): Double

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("() => (typeof performance !== 'undefined' ? performance.now() : Date.now())")
private external fun jsMonotonicMillis(): Double

/** Browser wall clock: `Date.now()` returns epoch milliseconds. */
actual fun nowMillis(): Long = jsNowMillis().toLong()

/** Monotonic millisecond clock: `performance.now()`, falling back to `Date.now()`. */
actual fun monotonicMillis(): Long = jsMonotonicMillis().toLong()

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    "(ms) => new Intl.DateTimeFormat(undefined, " +
        "{ month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false })" +
        ".format(new Date(ms))",
)
private external fun jsFormatShort(ms: Double): String

/** Local-time short label, matching the JVM `MMM d, HH:mm` shape. */
actual fun formatMonthDayHourMinute(epochMillis: Long): String = jsFormatShort(epochMillis.toDouble())
