// Modified from Hy4ri/hermes-mobile for this fork; see NOTICE.

package com.m57.hermescontrol.data.remote

import com.m57.hermescontrol.data.local.AuthManager
import io.ktor.client.HttpClient

/**
 * Android side of the [ApiClient] seam.
 *
 * No second HTTP stack is created: the gateway client is built *on top of* the
 * the connection pool, the persistent cookie jar, TLS pinning,
 * Ktor drives the same objects.
 */
internal actual object GatewayPlatform {
    actual fun endpoint(): ServerEndpoint = AuthManager.endpointForBuild()
}

/**
 * Android side of the [ApiClient] seam.
 *
 * The transport is Ktor on CIO (see [createGatewayTransportClient]): jar
 * cookies, the per-request bearer and the one-shot 401 refresh are Ktor
 * `HttpSend` interceptors, so no OkHttpClient is built here and OkHttp no
 * longer owns the gateway connection pool or its cookies.
 */
internal actual fun createGatewayHttpClient(auth: GatewayClientAuth): HttpClient =
    when (auth) {
        GatewayClientAuth.SessionCredentials -> createGatewayTransportClient(GatewayClientKind.Session)
        is GatewayClientAuth.FixedBearer ->
            createGatewayTransportClient(GatewayClientKind.FixedBearer, auth.token)
    }
