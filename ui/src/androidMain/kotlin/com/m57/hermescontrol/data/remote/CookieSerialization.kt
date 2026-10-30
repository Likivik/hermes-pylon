package com.m57.hermescontrol.data.remote

import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.parseServerSetCookieHeader

/**
 * The in-memory and on-disk cookie model.
 *
 * It was a projection of `okhttp3.Cookie`; it is now the model itself, so the
 * cookie stack no longer imports OkHttp. [matches][com.m57.hermescontrol.data.remote.matches]
 * lives in CookieMatching.kt — the rules OkHttp used to apply for us.
 *
 * `sameSite` stays nullable so payloads persisted by older builds still load.
 */
@kotlinx.serialization.Serializable
data class CookieHolder(
    val name: String,
    val value: String,
    val expiresAt: Long,
    val domain: String,
    val path: String,
    val secure: Boolean,
    val httpOnly: Boolean,
    val hostOnly: Boolean,
    val sameSite: String? = null,
)

/**
 * Wrap a raw `hermes_session_at` value (the pre-#470 single-string session
 * cookie) as a migration-only session cookie. OkHttp forbids constructing a
 * domainless cookie, so [LEGACY_WILDCARD_DOMAIN] marks the old unscoped value;
 * [PersistentCookieJar] recognizes that sentinel only within the active
 * default server scope. This matches the previous bare `Cookie` header
 * behavior for the one-release migration window.
 */
fun wrapSessionCookie(rawValue: String): CookieHolder? {
    val value = rawValue.trim()
    if (value.isEmpty()) return null
    // Far-future expiry (10 years) — session lifetime is owned by the
    // dashboard, not by client-side cookie expiry.
    return CookieHolder(
        name = SESSION_COOKIE_NAME,
        value = value,
        expiresAt = System.currentTimeMillis() + 10L * 365 * 24 * 60 * 60 * 1000,
        domain = LEGACY_WILDCARD_DOMAIN,
        path = "/",
        secure = false,
        httpOnly = true,
        hostOnly = true,
    )
}

const val LEGACY_WILDCARD_DOMAIN = "legacy-cookie.invalid"

const val SESSION_COOKIE_NAME = "hermes_session_at"

/**
 * Cookie-name variants emitted by Hermes Agent's dashboard-auth server.
 *
 * HTTPS deployments use `__Host-` at `/` and `__Secure-` under a reverse-
 * proxy path. HTTP loopback deployments use the bare name. Keep the strictest
 * variants first, matching the server's fallback order.
 */
val SESSION_COOKIE_NAMES: List<String> =
    listOf(
        "__Host-$SESSION_COOKIE_NAME",
        "__Secure-$SESSION_COOKIE_NAME",
        SESSION_COOKIE_NAME,
    )

/** True when [name] is any supported dashboard access-cookie name. */
fun isSessionCookieName(name: String): Boolean = name in SESSION_COOKIE_NAMES

/** True when [cookie] is the dashboard session cookie. */
fun isSessionCookie(cookie: CookieHolder): Boolean = isSessionCookieName(cookie.name)

/**
 * Parse one `Set-Cookie` header value into the stored model.
 *
 * Ktor's parser produces the wire facts; this maps them onto [CookieHolder]
 * with the two conventions the jar depends on: a cookie without an explicit
 * expiry gets [COOKIE_MAX_DATE_MILLIS] (OkHttp's session sentinel, which the jar
 * stores rather than prunes), and a cookie without a `Domain` attribute is
 * host-only for the request's host.
 */
fun parseSetCookie(
    url: Url,
    header: String,
): CookieHolder? {
    val parsed = runCatching { parseServerSetCookieHeader(header) }.getOrNull() ?: return null
    if (parsed.name.isEmpty()) return null
    val now = System.currentTimeMillis()
    val maxAge = parsed.maxAge
    val expiresAt =
        when {
            maxAge != null -> now + maxAge.toLong() * 1000L
            parsed.expires != null -> parsed.expires!!.timestamp
            else -> COOKIE_MAX_DATE_MILLIS
        }
    return CookieHolder(
        name = parsed.name,
        value = parsed.value,
        expiresAt = expiresAt,
        domain = parsed.domain?.removePrefix(".") ?: url.host,
        path = parsed.path?.ifEmpty { "/" } ?: "/",
        secure = parsed.secure,
        httpOnly = parsed.httpOnly,
        hostOnly = parsed.domain == null,
        sameSite = parsed.extensions["SameSite"],
    )
}

/** Parse every `Set-Cookie` header in [headers]. */
fun parseSetCookies(
    url: Url,
    headers: Set<Pair<String, String>>,
): List<CookieHolder> =
    headers
        .filter { (name, _) -> name.equals(HttpHeaders.SetCookie, ignoreCase = true) }
        .mapNotNull { (_, value) -> parseSetCookie(url, value) }
