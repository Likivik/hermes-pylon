package com.m57.hermescontrol.util

/** Wall-clock milliseconds since the epoch. */
expect fun nowMillis(): Long

/** Monotonic milliseconds since an arbitrary origin; only differences are meaningful. */
expect fun monotonicMillis(): Long

/**
 * Formats [epochMillis] as `yyyy-MM-dd'T'HH:mm:ss` in UTC.
 *
 * Replaces `java.text.SimpleDateFormat` + `java.util.TimeZone`, which do not
 * exist outside the JVM. The conversion is pure arithmetic (proleptic Gregorian),
 * so it is identical on every target.
 */
fun isoUtcSeconds(epochMillis: Long): String {
    var days = epochMillis.floorDiv(86_400_000L)
    var rem = epochMillis.mod(86_400_000L)

    val hour = rem / 3_600_000L
    rem %= 3_600_000L
    val minute = rem / 60_000L
    val second = (rem % 60_000L) / 1_000L

    // Howard Hinnant's civil_from_days, shifted to a March-based year.
    var z = days + 719_468L
    val era = (if (z >= 0) z else z - 146_096L) / 146_097L
    val doe = z - era * 146_097L
    val yoe = (doe - doe / 1_460L + doe / 36_524L - doe / 146_096L) / 365L
    var year = yoe + era * 400L
    val doy = doe - (365L * yoe + yoe / 4L - yoe / 100L)
    val mp = (5L * doy + 2L) / 153L
    val day = doy - (153L * mp + 2L) / 5L + 1L
    val month = if (mp < 10L) mp + 3L else mp - 9L
    if (month <= 2L) year += 1L

    fun pad(
        value: Long,
        width: Int,
    ): String = value.toString().padStart(width, '0')
    return "${pad(year, 4)}-${pad(month, 2)}-${pad(day, 2)}T" +
        "${pad(hour, 2)}:${pad(minute, 2)}:${pad(second, 2)}"
}

/**
 * Parses `yyyy-MM-dd'T'HH:mm:ss` (UTC, no zone suffix) back to epoch milliseconds.
 * Returns null when the text does not match that shape.
 */
fun parseIsoUtcSeconds(text: String): Long? {
    val m = Regex("^(\\d{4})-(\\d{2})-(\\d{2})T(\\d{2}):(\\d{2}):(\\d{2})$").find(text.trim()) ?: return null
    val (y, mo, d, h, mi, se) = m.destructured
    val year = y.toLong()
    val month = mo.toLong()
    val day = d.toLong()
    // days_from_civil, inverse of the formatter above
    val yy = if (month <= 2) year - 1 else year
    val era = (if (yy >= 0) yy else yy - 399) / 400
    val yoe = yy - era * 400
    val mp = if (month > 2) month - 3 else month + 9
    val doy = (153 * mp + 2) / 5 + day - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    val days = era * 146_097 + doe - 719_468
    return days * 86_400_000L +
        h.toLong() * 3_600_000L + mi.toLong() * 60_000L + se.toLong() * 1_000L
}

private fun Long.floorDiv(other: Long): Long = kotlin.math.floor(this.toDouble() / other).toLong()

private fun Long.mod(other: Long): Long = this - floorDiv(other) * other

/** Formats an instant as the short "MMM d, HH:mm" label used in lists, in local time. */
expect fun formatMonthDayHourMinute(epochMillis: Long): String
