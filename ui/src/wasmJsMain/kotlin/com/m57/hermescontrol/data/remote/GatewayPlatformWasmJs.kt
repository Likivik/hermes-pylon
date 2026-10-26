package com.m57.hermescontrol.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import kotlinx.browser.window

/**
 * Web side of the [ApiClient] seam.
 *
 * The page's own origin is the gateway: requests must stay same-origin, because
 * the dev server is plain HTTP on loopback while a tailnet origin is HTTPS, and
 * the proxy adds no CORS headers. The cleartext policy therefore follows the
 * scheme the page was loaded with.
 */
internal actual object GatewayPlatform {
    actual fun endpoint(): ServerEndpoint {
        val origin = window.location.origin
        val policy = if (origin.startsWith("http://")) CleartextPolicy.ALLOW_WITH_WARNING else CleartextPolicy.DENY
        return ServerEndpoint.parse(rawValue = "$origin/", cleartextPolicy = policy)
    }
}

internal actual fun createGatewayHttpClient(auth: GatewayClientAuth): HttpClient =
    when (auth) {
        // Same-origin browser session: cookies travel with the request.
        GatewayClientAuth.SessionCredentials -> createHermesHttpClient()
        is GatewayClientAuth.FixedBearer -> {
            val token = auth.token
            createHermesHttpClient().config {
                if (token.isNotBlank()) {
                    defaultRequest {
                        headers.append(HttpHeaders.Authorization, "Bearer $token")
                    }
                }
            }
        }
    }
