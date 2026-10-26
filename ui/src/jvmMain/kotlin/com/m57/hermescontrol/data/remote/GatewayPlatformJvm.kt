package com.m57.hermescontrol.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders

/**
 * Desktop/JVM side of the [ApiClient] seam: a plain Ktor client, endpoint from
 * the `hermes.gatewayUrl` system property (default: a local gateway).
 */
internal actual object GatewayPlatform {
    actual fun endpoint(): ServerEndpoint {
        val raw = System.getProperty("hermes.gatewayUrl") ?: "http://127.0.0.1:8787/"
        return ServerEndpoint.parse(rawValue = raw, cleartextPolicy = CleartextPolicy.ALLOW_WITH_WARNING)
    }
}

internal actual fun createGatewayHttpClient(auth: GatewayClientAuth): HttpClient =
    when (auth) {
        // No session store on desktop: the local gateway is unauthenticated.
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
