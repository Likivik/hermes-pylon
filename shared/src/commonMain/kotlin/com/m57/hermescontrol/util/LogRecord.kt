package com.m57.hermescontrol.util

/**
 * Severity, ordered. Mirrors the OpenTelemetry log data model's severity bands
 * closely enough to map later without a translation table, and without pulling
 * the SDK into a target that ships to a phone.
 */
enum class LogSeverity {
    VERBOSE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    ;

    /** True when a sink configured at [threshold] should keep records of this severity. */
    fun passes(threshold: LogSeverity): Boolean = ordinal >= threshold.ordinal
}

/**
 * The one log record shape, client and host.
 *
 * Field choices worth defending:
 * - [component] is a short, stable, greppable name (`net`, `ws`, `auth`), not a
 *   package name: package names move during refactors and break saved queries.
 * - [attributes] is deliberately a flat `Map<String, String>`: nested structures
 *   are unreadable in a terminal and impossible to filter on a phone. If a value
 *   needs structure, encode it as a short JSON string under one key.
 * - [requestId] is a first-class field rather than an attribute, because joining
 *   client and host logs on it is the entire point of correlation.
 * - [errorType] carries the exception's simple name only. Stack traces are the
 *   platform sink's job (Logcat prints them); the ring must stay bounded.
 */
data class LogRecord(
    val seq: Long,
    val atMillis: Long,
    val severity: LogSeverity,
    val component: String,
    val message: String,
    val attributes: Map<String, String> = emptyMap(),
    val errorType: String? = null,
    val requestId: String? = null,
) {
    /** Single-line rendering for consoles, share bundles and test assertions. */
    fun render(): String =
        buildString {
            append(component)
            append(' ')
            append(severity.name)
            append(' ')
            append(message)
            errorType?.let { append(" error=").append(it) }
            requestId?.let { append(" req=").append(it) }
            attributes.forEach { (key, value) ->
                append(' ')
                append(key)
                append('=')
                append(value)
            }
        }
}
