package com.m57.hermescontrol.data.remote

import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Spec for the Ktor transport floor. Uses MockEngine — the multiplatform
 * replacement for MockWebServer — so this runs on every target.
 */
class HermesHttpClientTest {
    @Serializable
    private data class Probe(val toolCount: Int)

    private fun jsonEngine(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = MockEngine {
        respond(
            content = body,
            status = status,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )
    }

    @Test
    fun decodesSnakeCasePayloadsWithHermesJson() =
        runTest {
            val client = createHermesHttpClient(jsonEngine("""{"tool_count":3}"""))
            val probe = client.get("https://gw.example/hermes/api/mcp").body<Probe>()
            assertEquals(3, probe.toolCount)
        }

    @Test
    fun serverErrorsAreValuesNotExceptions() =
        runTest {
            val client = createHermesHttpClient(jsonEngine("nope", HttpStatusCode.InternalServerError))
            val response = client.get("https://gw.example/hermes/api/mcp")
            assertEquals(HttpStatusCode.InternalServerError, response.status)
        }

    @Test
    fun retriesConnectionFailuresUpToMaxRetries() =
        runTest {
            var attempts = 0
            val engine =
                MockEngine {
                    attempts++
                    throw IOException("connection refused")
                }
            val client = createHermesHttpClient(engine)
            assertFailsWith<IOException> { client.get("https://gw.example/hermes/api/mcp") }
            assertEquals(HermesHttpConfig.Default.maxRetries + 1, attempts)
        }

    @Test
    fun defaultConfigMirrorsOkHttpProviderTimeouts() {
        assertEquals(15_000, HermesHttpConfig.Default.connectTimeoutMillis)
        assertEquals(30_000, HermesHttpConfig.Default.requestTimeoutMillis)
        assertEquals(30_000, HermesHttpConfig.Default.socketTimeoutMillis)
    }

    @Test
    fun probeAndStreamingProfilesMirrorTheOkHttpVariants() {
        assertEquals(5_000, HermesHttpConfig.Probe.connectTimeoutMillis)
        assertEquals(5_000, HermesHttpConfig.Probe.requestTimeoutMillis)
        assertEquals(Long.MAX_VALUE, HermesHttpConfig.streaming().requestTimeoutMillis)
        assertEquals(Long.MAX_VALUE, HermesHttpConfig.streaming().socketTimeoutMillis)
    }
}
