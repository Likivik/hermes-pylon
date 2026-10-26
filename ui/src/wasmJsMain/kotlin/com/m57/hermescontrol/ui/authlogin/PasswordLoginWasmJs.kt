package com.m57.hermescontrol.ui.authlogin

import com.m57.hermescontrol.data.remote.HermesHttpConfig
import com.m57.hermescontrol.data.remote.ServerEndpoint
import com.m57.hermescontrol.data.remote.createHermesHttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

/**
 * Web login POST.
 *
 * The browser owns the session: the request is same-origin, so the engine's
 * fetch sends existing cookies and the `Set-Cookie` on this response is stored
 * and replayed by the browser itself. Nothing to persist here — which is also
 * why the wasm target needs no cookie jar.
 */
internal actual suspend fun performPasswordLogin(
    endpoint: ServerEndpoint,
    jsonBody: String,
): Int =
    createHermesHttpClient(HermesHttpConfig.Probe).use { client ->
        client
            .post(endpoint.baseUrl.resolve("auth/password-login").toString()) {
                contentType(ContentType.Application.Json)
                setBody(jsonBody)
            }.status.value
    }
