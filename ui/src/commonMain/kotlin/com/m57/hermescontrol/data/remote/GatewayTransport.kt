package com.m57.hermescontrol.data.remote

import com.m57.hermescontrol.data.local.AuthManager
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode

/**
 * The Android gateway transport, on Ktor's own engine and plugins.
 *
 * Three behaviours used to live in OkHttp interceptors and an `Authenticator`;
 * they are Ktor constructs here with unchanged semantics:
 *
 * 1. **Cookies** — before a request the persistent jar's cookies for that URL
 *    are attached; after it, every `Set-Cookie` is parsed and stored under the
 *    credential boundary captured *before* the request, so a cookie that arrives
 *    after a profile switch or token rotation is dropped rather than replayed
 *    into the next generation (issue #470).
 * 2. **Bearer** — read per request, so a token refreshed mid-flight is used by
 *    the retry instead of the stale value baked into the call.
 * 3. **401 refresh-retry** — one attempt only, never in gated mode (where the
 *    cookie is the credential and `safeApiCall` owns the expired-session path).
 */
enum class GatewayClientKind {
    /** Session credentials: jar cookies plus a per-request bearer when available. */
    Session,

    /** A fixed bearer, no retry, no coupling to global auth state. */
    FixedBearer,
}

/**
 * Build a gateway client bound to [kind].
 *
 * The engine is whatever the shared floor resolves for this platform (CIO on
 * Android): no caller-owned OkHttpClient, no `preconfigured` seam.
 */
fun createGatewayTransportClient(
    kind: GatewayClientKind,
    token: String? = null,
    config: HermesHttpConfig = HermesHttpConfig.Default,
): HttpClient {
    val client = createHermesHttpClient(config)
    return when (kind) {
        GatewayClientKind.Session -> client.withJarCookies().withSessionBearer()
        GatewayClientKind.FixedBearer -> client.withFixedBearer(token)
    }
}

/** Attach the jar's cookies before the request and persist `Set-Cookie` after it. */
private fun HttpClient.withJarCookies(): HttpClient =
    apply {
        plugin(HttpSend).intercept { request ->
            val jar = if (CookieManager.isInitialized()) CookieManager.cookieJar else null
            val boundary = jar?.captureCredentialBoundary()
            if (jar != null) {
                val outgoing = jar.loadForRequest(request.url.build())
                if (outgoing.isNotEmpty()) {
                    request.headers.append(
                        HttpHeaders.Cookie,
                        outgoing.joinToString("; ") { "${it.name}=${it.value}" },
                    )
                }
            }
            val call = execute(request)
            if (jar != null && boundary != null) {
                val url = request.url.build()
                val incoming =
                    parseSetCookies(
                        url,
                        call.response.headers.entries().flatMap { (name, values) ->
                            values.map { name to it }
                        }.toSet(),
                    )
                if (incoming.isNotEmpty()) jar.saveFromResponse(boundary, url, incoming)
            }
            call
        }
    }

/** Fixed bearer, no jar, no retry: the `tempService` client. */
private fun HttpClient.withFixedBearer(token: String?): HttpClient =
    apply {
        plugin(HttpSend).intercept { request ->
            gatewayBearerValue(gated = false, token = token)?.let {
                request.headers[HttpHeaders.Authorization] = it
            }
            execute(request)
        }
    }

/** Per-request bearer + one 401 refresh-retry (loopback mode only). */
private fun HttpClient.withSessionBearer(): HttpClient =
    apply {
        plugin(HttpSend).intercept { request ->
            gatewayBearerValue(AuthManager.isGatedMode(), AuthManager.getToken())?.let {
                request.headers[HttpHeaders.Authorization] = it
            }
            val call = execute(request)
            // Only loopback mode can refresh a bearer; in gated mode the cookie
            // is the credential and the 401 belongs to safeApiCall's
            // expired-session recovery.
            if (call.response.status != HttpStatusCode.Unauthorized) return@intercept call
            if (AuthManager.isGatedMode()) return@intercept call
            val fresh = DashboardSessionTokenRefresher.refresh() ?: return@intercept call
            // Re-execute the same builder with the refreshed value: exactly one
            // retry, mirroring the old Authenticator's `priorResponse != null` guard.
            request.headers[HttpHeaders.Authorization] = "Bearer $fresh"
            execute(request)
        }
    }

/**
 * Media transport: one credential snapshot, no jar, no retry.
 *
 * Managed-file reads bind to the credentials captured when the request context
 * was built, so a mid-download token refresh cannot change what the request is
 * authenticated with (that is the whole point of [MediaRequestContext]).
 */
fun createMediaTransportClient(
    token: String?,
    cookieHeader: String?,
): HttpClient =
    createHermesHttpClient().apply {
        plugin(HttpSend).intercept { request ->
            if (!cookieHeader.isNullOrBlank()) {
                request.headers[HttpHeaders.Cookie] = cookieHeader
            } else {
                gatewayBearerValue(gated = false, token = token)?.let {
                    request.headers[HttpHeaders.Authorization] = it
                }
            }
            execute(request)
        }
    }
