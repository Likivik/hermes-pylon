package com.m57.hermescontrol.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.text.DateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
actual fun rememberIs24HourFormat(): Boolean =
    remember {
        DateFormat.getTimeInstance(DateFormat.SHORT).format(java.util.Date(0)).contains(":") &&
            !DateFormat.getTimeInstance(DateFormat.SHORT).format(java.util.Date(0)).lowercase().let {
                it.contains("am") || it.contains("pm")
            }
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
