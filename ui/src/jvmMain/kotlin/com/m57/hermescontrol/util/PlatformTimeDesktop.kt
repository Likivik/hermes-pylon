package com.m57.hermescontrol.util

actual fun nowMillis(): Long = System.currentTimeMillis()

actual fun monotonicMillis(): Long = System.nanoTime() / 1_000_000L

actual fun formatMonthDayHourMinute(epochMillis: Long): String =
    java.text.SimpleDateFormat("MMM d, HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(epochMillis))
