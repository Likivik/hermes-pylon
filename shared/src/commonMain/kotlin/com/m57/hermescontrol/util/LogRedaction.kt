package com.m57.hermescontrol.util

/**
 * Redaction, applied at the sink boundary rather than at call sites.
 *
 * Rationale: call sites forget, and the ones that forget are the interesting
 * ones. A logger that trusts its callers to strip credentials is a credential
 * leak waiting for the first hurried commit. Everything here is deterministic
 * and dependency-free so it can run on wasm too.
 */
object LogRedaction {
    /** Header/attribute names whose values are never recorded. */
    private val SECRET_KEYS: Set<String> =
        setOf(
            "authorization",
            "cookie",
            "set-cookie",
            "x-api-key",
            "proxy-authorization",
            "token",
            "ticket",
            "password",
            "apikey",
            "api_key",
        )

    /** Header names that are safe *and* useful when recorded. Anything else is dropped. */
    private val RECORDED_HEADERS: Set<String> =
        setOf(
            "accept",
            "content-type",
            "content-length",
            "server",
            "location",
            "x-pylon-request-id",
        )

    /** `<redacted len=N>` keeps "was it even set, and roughly how big" without the value. */
    fun mask(value: String?): String =
        when {
            value == null -> "<absent>"
            value.isEmpty() -> "<empty>"
            else -> "<redacted len=${value.length}>"
        }

    fun isSecretKey(key: String): Boolean = key.lowercase() in SECRET_KEYS

    /**
     * A URL safe to log: scheme, host, path. Query and fragment are dropped
     * wholesale — gateway URLs carry tickets and tokens there, and a per-parameter
     * allowlist would need updating every time the gateway adds one.
     */
    fun url(raw: String): String {
        val withoutFragment = raw.substringBefore('#')
        val withoutQuery = withoutFragment.substringBefore('?')
        // Strip userinfo (`user:pass@host`) if anyone ever passes one in.
        val schemeEnd = withoutQuery.indexOf("://")
        if (schemeEnd >= 0) {
            val authorityStart = schemeEnd + 3
            val authorityEnd = withoutQuery.indexOf('/', authorityStart).let { if (it < 0) withoutQuery.length else it }
            val authority = withoutQuery.substring(authorityStart, authorityEnd)
            if ('@' in authority) {
                val host = authority.substringAfterLast('@')
                return withoutQuery.substring(0, authorityStart) + host + withoutQuery.substring(authorityEnd)
            }
        }
        return withoutQuery
    }

    /** A header value safe to log: known-secret headers masked, unknown headers dropped. */
    fun header(
        name: String,
        value: String,
    ): String? =
        when {
            isSecretKey(name) -> mask(value)
            name.lowercase() in RECORDED_HEADERS -> value.take(120)
            else -> null
        }

    /** Filters an attribute map: secret keys masked, values trimmed, cap on count. */
    fun attributes(map: Map<String, String>): Map<String, String> =
        map.entries
            .take(MAX_ATTRIBUTES)
            .associate { (key, value) ->
                key to if (isSecretKey(key)) mask(value) else value.take(MAX_VALUE_LENGTH)
            }

    private const val MAX_ATTRIBUTES = 12
    private const val MAX_VALUE_LENGTH = 200
}
