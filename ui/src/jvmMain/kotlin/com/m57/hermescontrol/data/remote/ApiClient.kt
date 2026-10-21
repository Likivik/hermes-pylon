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

/**
 * Desktop/JVM gateway factory: a plain Ktor client. The endpoint comes from the
 * `hermes.gatewayUrl` system property, defaulting to a local gateway.
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
        val endpoint = endpoint()
        val http = cachedClient ?: createHermesHttpClient().also { cachedClient = it }
        val response =
            http.request(
                endpoint.baseUrl.toString().trimEnd('/') + "/" + path.trimStart('/'),
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

    private fun endpoint(): ServerEndpoint {
        val raw = System.getProperty("hermes.gatewayUrl") ?: "http://127.0.0.1:8787/"
        return ServerEndpoint.parse(rawValue = raw, cleartextPolicy = CleartextPolicy.ALLOW_WITH_WARNING)
    }

    private fun build(): HermesGatewayApi = HermesGatewayApi(createHermesHttpClient(), endpoint().baseUrl)

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
