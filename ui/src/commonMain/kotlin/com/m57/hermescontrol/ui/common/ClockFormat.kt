package com.m57.hermescontrol.ui.common

import androidx.compose.runtime.Composable

/**
 * Whether the user's locale prefers a 24-hour clock.
 *
 * Android reads the real system setting; desktop and web infer it from the locale,
 * so the value can differ from an explicit Android override.
 */
@Composable
expect fun rememberIs24HourFormat(): Boolean

/**
 * Local wall-clock time for [epochMillis], formatted as `HH:mm` or `h:mm a`.
 *
 * Replaces `DateTimeFormatter.ofPattern(...).withZone(ZoneId.systemDefault())`,
 * which is JVM-only.
 */
expect fun formatClockTime(
    epochMillis: Long,
    is24Hour: Boolean,
): String
