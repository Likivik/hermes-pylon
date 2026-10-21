package com.m57.hermescontrol.util

private val MONTH_ABBREVIATIONS =
    listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

/**
 * Renders an ISO-8601 UTC timestamp (`yyyy-MM-dd'T'HH:mm:ss`, with or without a
 * trailing `Z`) as `MMM d, yyyy HH:mm`.
 *
 * Written as plain arithmetic on purpose: `SimpleDateFormat` and `TimeZone` are
 * JVM-only, and this is the only place the UI needs a calendar date rather than a
 * clock time. Returns the input unchanged when it does not parse, so a malformed
 * server value still shows something instead of an empty cell.
 */
fun formatIsoTimestamp(
    isoDate: String,
    withDate: Boolean = true,
): String {
    val parts = (parseIsoParts(isoDate) ?: return isoDate).plusMinutes(localUtcOffsetMinutes())
    val monthLabel = MONTH_ABBREVIATIONS.getOrNull(parts.month - 1) ?: return isoDate
    val time =
        parts.hour.toString().padStart(2, '0') + ":" + parts.minute.toString().padStart(2, '0')
    return if (withDate) "$monthLabel ${parts.day}, ${parts.year} $time" else time
}

/** Shifts a UTC instant into local wall-clock time, adjusting the date if needed. */
private fun IsoParts.plusMinutes(offsetMinutes: Int): IsoParts {
    if (offsetMinutes == 0) return this
    val total = hour * 60 + minute + offsetMinutes
    val dayShift = floorDiv(total, 24 * 60)
    val shifted = total - dayShift * 24 * 60
    return copy(hour = shifted / 60, minute = shifted % 60, day = day + dayShift)
}

private data class IsoParts(
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int,
    val minute: Int,
)

private fun parseIsoParts(value: String): IsoParts? {
    // Expect at least `yyyy-MM-ddTHH:mm`; seconds and any zone suffix are optional.
    if (value.length < 16) return null
    return try {
        val year = value.substring(0, 4).toInt()
        val month = value.substring(5, 7).toInt()
        val day = value.substring(8, 10).toInt()
        val hour = value.substring(11, 13).toInt()
        val minute = value.substring(14, 16).toInt()
        if (month !in 1..12 || day !in 1..31 || hour !in 0..23 || minute !in 0..59) {
            null
        } else {
            IsoParts(year, month, day, hour, minute)
        }
    } catch (_: NumberFormatException) {
        null
    }
}

/** Integer division rounding toward negative infinity, without java.lang.Math. */
private fun floorDiv(
    a: Int,
    b: Int,
): Int {
    val q = a / b
    return if (a % b != 0 && (a < 0) != (b < 0)) q - 1 else q
}
