package com.m57.hermescontrol.ui.common

import kotlin.js.ExperimentalWasmJsInterop

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("(n) => new Intl.NumberFormat().format(n)")
private external fun jsFormatInteger(n: Double): String

actual fun formatIntegerGrouped(value: Long): String = jsFormatInteger(value.toDouble())
