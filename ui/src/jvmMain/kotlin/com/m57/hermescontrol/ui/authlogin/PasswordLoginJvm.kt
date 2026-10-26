package com.m57.hermescontrol.ui.authlogin

import com.m57.hermescontrol.data.remote.HermesHttpConfig
import com.m57.hermescontrol.data.remote.ServerEndpoint
import com.m57.hermescontrol.data.remote.createHermesHttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType

/**
 * Desktop login POST.
 *
 * Known gap, deliberately not papered over: there is no shared cookie store on
 * this target yet, so the session cookie this response carries is not persisted
 * — a desktop sign-in validates the credentials but the following REST and
 * WebSocket calls have nothing to send. That gap closes with the cookie
 * tranche; shared code must not pretend otherwise.
 */
internal actual suspend fun performPasswordLogin(
    endpoint: ServerEndpoint,
    jsonBody: String,
): Int =
    createHermesHttpClient(HermesHttpConfig.Probe).use { client ->
        client
            .post(endpoint.baseUrl.resolve("auth/password-login").toString()) {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Accept, ContentType.Application.Json.toString())
                setBody(jsonBody)
            }.status.value
    }
