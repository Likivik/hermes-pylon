package com.m57.hermescontrol.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlin.js.ExperimentalWasmJsInterop

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    "() => { try { const o = new Intl.DateTimeFormat(undefined," +
        " {hour:'2-digit', minute:'2-digit'}).resolvedOptions();" +
        " return o.hourCycle === 'h23' || o.hourCycle === 'h24'; }" +
        " catch (e) { return false; } }",
)
private external fun jsIs24HourLocale(): Boolean

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    "(ms, is24) => new Intl.DateTimeFormat(undefined, is24" +
        " ? {hour:'2-digit', minute:'2-digit', hourCycle:'h23'}" +
        " : {hour:'numeric', minute:'2-digit', hour12:true}).format(new Date(ms))",
)
private external fun jsFormatClockTime(
    epochMillis: Double,
    is24Hour: Boolean,
): String

@Composable
actual fun rememberIs24HourFormat(): Boolean = remember { jsIs24HourLocale() }

actual fun formatClockTime(
    epochMillis: Long,
    is24Hour: Boolean,
): String = jsFormatClockTime(epochMillis.toDouble(), is24Hour)
