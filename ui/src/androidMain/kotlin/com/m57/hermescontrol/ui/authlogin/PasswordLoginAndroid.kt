package com.m57.hermescontrol.ui.authlogin

import com.m57.hermescontrol.data.remote.CookieManager
import com.m57.hermescontrol.data.remote.GatewayClientKind
import com.m57.hermescontrol.data.remote.HermesHttpConfig
import com.m57.hermescontrol.data.remote.ServerEndpoint
import com.m57.hermescontrol.data.remote.createGatewayTransportClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders

/**
 * The gated login POST.
 *
 * Same transport as every other gateway call now — Ktor on CIO with the probe
 * timeouts — and `CookieManager.beginAuthentication()` still opens the window in
 * which the persistent jar accepts the `Set-Cookie` this response carries
 * (issue #470 depends on that).
 */
internal actual suspend fun performPasswordLogin(
    endpoint: ServerEndpoint,
    jsonBody: String,
): Int {
    val client =
        createGatewayTransportClient(
            GatewayClientKind.Session,
            config = HermesHttpConfig.Probe,
        )
    return try {
        CookieManager.beginAuthentication()
        client
            .post(endpoint.baseUrl.toString().trimEnd('/') + "/auth/password-login") {
                header(HttpHeaders.ContentType, "application/json")
                setBody(jsonBody)
            }.status
            .value
    } finally {
        client.close()
    }
}
