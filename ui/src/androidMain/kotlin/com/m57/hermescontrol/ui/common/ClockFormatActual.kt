@file:Suppress("ktlint:standard:filename")

package com.m57.hermescontrol.ui.common

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
actual fun rememberIs24HourFormat(): Boolean {
    val context = LocalContext.current
    return remember(context) { DateFormat.is24HourFormat(context) }
}

actual fun formatClockTime(
    epochMillis: Long,
    is24Hour: Boolean,
): String {
    val pattern = if (is24Hour) "HH:mm" else "h:mm a"
    return DateTimeFormatter
        .ofPattern(pattern)
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(epochMillis))
}
