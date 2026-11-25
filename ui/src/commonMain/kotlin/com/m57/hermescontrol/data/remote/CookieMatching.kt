/**
 * RFC 6265 matching for [CookieHolder].
 *
 * This is the part OkHttp used to give us for free via `Cookie.matches(url)`.
 * It lives here, in one place, because the jar must answer exactly the same
 * question — "which stored cookies does this request carry?" — now that it no
 * longer borrows OkHttp's cookie model. `PersistentCookieJarTest` (535 lines)
 * is the spec for these rules; do not relax them to make a test pass.
 */
package com.m57.hermescontrol.data.remote

import io.ktor.http.Url

/**
 * True when this cookie should be sent with a request to [url].
 *
 * Expiry is the caller's business ([PersistentCookieJar.loadForRequest] filters
 * on it), because a stale cookie must be *dropped*, not merely withheld.
 */
fun CookieHolder.matches(url: Url): Boolean {
    if (secure && !url.protocol.name.equals("https", ignoreCase = true)) return false
    if (!domainMatches(url.host)) return false
    if (!pathMatches(url.encodedPath)) return false
    return true
}

/**
 * RFC 6265 §5.1.3. A host-only cookie matches only its exact host; a domain
 * cookie matches the domain and any subdomain of it.
 */
fun CookieHolder.domainMatches(host: String): Boolean {
    val candidate = host.lowercase().removeSuffix(".")
    val cookieDomain = domain.lowercase().removePrefix(".").removeSuffix(".")
    if (cookieDomain.isEmpty()) return false
    if (hostOnly) return candidate == cookieDomain
    return candidate == cookieDomain || candidate.endsWith(".$cookieDomain")
}

/** RFC 6265 §5.1.4 path-matching. */
fun CookieHolder.pathMatches(requestPath: String): Boolean {
    val cookiePath = path.ifEmpty { "/" }
    val reqPath = requestPath.ifEmpty { "/" }
    if (cookiePath == "/") return true
    if (reqPath == cookiePath) return true
    if (!reqPath.startsWith(cookiePath)) return false
    // "/foo" matches "/foo/bar" but not "/foobar".
    return cookiePath.endsWith("/") || reqPath[cookiePath.length] == '/'
}

// Session cookies: OkHttp's "no expiry" sentinel, far in the future. The jar
// prunes anything with expiresAt <= now, so a cookie without an explicit
// expiry must carry this rather than 0.
const val COOKIE_MAX_DATE_MILLIS: Long = 253_402_300_799_000L
