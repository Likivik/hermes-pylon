package com.m57.hermescontrol.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.timeout
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.browser.window

/**
 * Web gateway factory.
 *
 * The page's own origin is the gateway: requests must stay same-origin, because the
 * dev server is plain HTTP on loopback while a tailnet origin is HTTPS, and the
 * proxy adds no CORS headers. The cleartext policy therefore follows the scheme the
 * page was loaded with.
 */
actual object ApiClient {
    private var cached: HermesGatewayApi? = null
    private var cachedClient: HttpClient? = null

    actual val hermesApi: HermesGatewayApi
        get() = cached ?: build().also { cached = it }

    actual fun rebuild() {
        cached = null
        cachedClient = null
    }

    actual suspend fun postJson(
        path: String,
        jsonBody: String,
        timeoutMillis: Long,
    ): Int {
        val http = cachedClient ?: createHermesHttpClient().also { cachedClient = it }
        val origin = window.location.origin.trimEnd('/')
        val response =
            http.request(
                "$origin/" + path.trimStart('/'),
            ) {
                method = HttpMethod.Post
                contentType(ContentType.Application.Json)
                setBody(jsonBody)
                timeout {
                    requestTimeoutMillis = timeoutMillis
                    connectTimeoutMillis = timeoutMillis
                }
            }
        return response.status.value
    }

    private fun build(): HermesGatewayApi {
        val origin = window.location.origin
        val policy = if (origin.startsWith("http://")) CleartextPolicy.ALLOW_WITH_WARNING else CleartextPolicy.DENY
        val endpoint =
            ServerEndpoint.parse(
                rawValue = "$origin/",
                cleartextPolicy = policy,
            )
        return HermesGatewayApi(createHermesHttpClient(), endpoint.baseUrl)
    }

    actual fun tempService(
        baseUrl: String,
        token: String,
    ): HermesGatewayApi {
        val endpoint = ServerEndpoint.parseForBuild(baseUrl)
        val client =
            createHermesHttpClient().config {
                if (token.isNotBlank()) {
                    defaultRequest {
                        headers.append(HttpHeaders.Authorization, "Bearer $token")
                    }
                }
            }
        return HermesGatewayApi(client, endpoint.baseUrl)
    }
}
