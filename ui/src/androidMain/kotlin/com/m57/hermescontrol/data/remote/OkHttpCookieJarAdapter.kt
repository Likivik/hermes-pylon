package com.m57.hermescontrol.data.remote

import io.ktor.http.Url
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Adapts the OkHttp-free [PersistentCookieJar] to OkHttp's `CookieJar`.
 *
 * This is the only place the cookie stack touches OkHttp's cookie model, and it
 * exists purely because OkHttp *is* the engine until the engine swap. When the
 * engine moves to CIO this file is deleted with it; nothing in
 * [PersistentCookieJar], [CookieStore] or [CookieManager] needs to change.
 */
class OkHttpCookieJarAdapter(
    private val jarProvider: () -> PersistentCookieJar,
) : CookieJar {
    /** The jar this client is bound to. Lets tests and callers unwrap it. */
    val jar: PersistentCookieJar
        get() = jarProvider()

    override fun saveFromResponse(
        url: HttpUrl,
        cookies: List<Cookie>,
    ) {
        jar.saveFromResponse(Url(url.toString()), cookies.mapNotNull { it.toHolder(url) })
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        jar.loadForRequest(Url(url.toString())).mapNotNull { it.toOkHttpCookie() }
}

/** Project an OkHttp cookie into the stored model, keeping its host-only flag. */
internal fun Cookie.toHolder(url: HttpUrl): CookieHolder? =
    runCatching {
        CookieHolder(
            name = name,
            value = value,
            expiresAt = expiresAt,
            domain = domain,
            path = path,
            secure = secure,
            httpOnly = httpOnly,
            // OkHttp exposes host-only cookies as a domain that ignores subdomains.
            hostOnly = !domain.startsWith(".") && domain == url.host,
            sameSite = sameSite,
        )
    }.getOrNull()

/** Rebuild an OkHttp cookie from the stored model, for the engine's jar. */
internal fun CookieHolder.toOkHttpCookie(): Cookie? =
    runCatching {
        Cookie
            .Builder()
            .name(name)
            .value(value)
            .expiresAt(expiresAt)
            .apply { if (hostOnly) hostOnlyDomain(domain) else domain(domain) }
            .path(path)
            .also { if (secure) it.secure() }
            .also { if (httpOnly) it.httpOnly() }
            .also { if (sameSite != null) it.sameSite(sameSite) }
            .build()
    }.getOrNull()
