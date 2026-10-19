package com.m57.hermescontrol.ui.common

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Locale-aware integer grouping (thousands separators), the multiplatform
 * replacement for `NumberFormat.getIntegerInstance(Locale.getDefault())`.
 *
 * Android/JVM use `NumberFormat`; wasmJs uses `Intl.NumberFormat`, so each
 * target keeps the user's real separators instead of a hard-coded comma.
 */
expect fun formatIntegerGrouped(value: Long): String

/**
 * One decimal place with a `.` separator — the commonCode equivalent of
 * `String.format(Locale.US, "%.1f", value)`.
 *
 * Kept locale-independent on purpose: the original pinned `Locale.US`, and
 * this is used inside compact chip labels (`12.3k`) where a locale decimal
 * comma would read as two numbers.
 */
fun formatOneDecimal(value: Double): String {
    val tenths = (value * 10).roundToInt()
    val whole = tenths / 10
    val frac = abs(tenths % 10)
    return "$whole.$frac"
}

/** Two decimal places with a `.` separator — the multiplatform `"%.2f".format(v)`. */
fun formatTwoDecimals(value: Double): String {
    val hundredths = (value * 100).roundToInt()
    val whole = hundredths / 100
    val frac = abs(hundredths % 100)
    return "$whole.${frac.toString().padStart(2, '0')}"
}
