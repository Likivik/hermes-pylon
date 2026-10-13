package com.m57.hermescontrol.util

import kotlin.time.Clock

/** wasmJs: the stdlib clock, which the browser backs with `Date.now()`. */
actual fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()
