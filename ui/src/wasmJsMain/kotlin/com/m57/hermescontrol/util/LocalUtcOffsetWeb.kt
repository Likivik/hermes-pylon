@file:Suppress("ktlint:standard:filename")

package com.m57.hermescontrol.util

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("() => -new Date().getTimezoneOffset()")
private external fun browserOffsetMinutes(): Int

actual fun localUtcOffsetMinutes(): Int = browserOffsetMinutes()
