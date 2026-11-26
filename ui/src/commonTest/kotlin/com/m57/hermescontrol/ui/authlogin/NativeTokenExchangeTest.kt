package com.m57.hermescontrol.ui.authlogin

import com.m57.hermescontrol.data.remote.HermesHttpConfig
import com.m57.hermescontrol.data.remote.createHermesHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the exact request the gateway will receive and the exact mapping of its status codes.
 *
 * Retries are off (maxRetries = 0) for one reason: Ktor's retry plugin re-sends 5xx by default,
 * so a `503` test would silently see three requests and pass for the wrong reason.
 */
class NativeTokenExchangeTest {
    private val base = "http://127.0.0.1:9119"
    private var requests = 0

    private fun engine(
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String,
        onRequest: (io.ktor.client.request.HttpRequestData) -> Unit = {},
    ): MockEngine =
        MockEngine { request ->
            requests += 1
            onRequest(request)
            respond(
                content = body,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

    private fun exchange(engine: MockEngine) =
        NativeTokenExchange(
            createHermesHttpClient(engine, HermesHttpConfig.Default.copy(maxRetries = 0)),
        )

    private val successBody =
        """
        {"access_token":"at-1","refresh_token":"rt-1","token_type":"Bearer",
         "expires_at":1893456000,"provider":"nous","user_id":"u-1"}
        """.trimIndent()

    @Test
    fun redeemPostsCodeAndVerifierAsJson() =
        runTest {
            var seenUrl = ""
            var seenMethod = ""
            var seenBody = ""
            var seenContentType = ""

            val result =
                exchange(
                    engine(body = successBody) { request ->
                        seenUrl = request.url.toString()
                        seenMethod = request.method.value
                        seenContentType = request.body.contentType?.toString().orEmpty()
                        seenBody = (request.body as TextContent).text
                    },
                ).redeem(base, code = "the-code", codeVerifier = "the-verifier")

            assertEquals("http://127.0.0.1:9119/auth/native/token", seenUrl)
            assertEquals("POST", seenMethod)
            assertTrue(seenContentType.startsWith("application/json"), "gateway reads a JSON body")
            assertTrue(seenBody.contains("\"code\":\"the-code\""), "body was $seenBody")
            assertTrue(seenBody.contains("\"code_verifier\":\"the-verifier\""), "body was $seenBody")
            assertIs<TokenResult.Success>(result)
        }

    /**
     * The native flow sets no cookie, and sending one would authenticate the request as whatever
     * browser session the client happens to hold. The exchange owns a cookie-less client; this test
     * is what stops a future refactor from handing it the shared one.
     */
    @Test
    fun redeemSendsNoCookie() =
        runTest {
            var cookie: String? = null

            exchange(engine(body = successBody) { request -> cookie = request.headers[HttpHeaders.Cookie] })
                .redeem(base, code = "c", codeVerifier = "v")

            assertNull(cookie, "the token endpoint must not receive a session cookie")
        }

    @Test
    fun redeemParsesTheBearerPair() =
        runTest {
            val result = exchange(engine(body = successBody)).redeem(base, "c", "v")

            val token = assertIs<TokenResult.Success>(result).token
            assertEquals("at-1", token.accessToken)
            assertEquals("rt-1", token.refreshToken)
            assertEquals("nous", token.provider)
            assertEquals(1893456000L, token.expiresAtEpochSeconds)
        }

    @Test
    fun redeemToleratesAnExpiryWrittenAsAString() =
        runTest {
            val body = """{"access_token":"a","refresh_token":"r","expires_at":"1893456000"}"""

            val token = assertIs<TokenResult.Success>(exchange(engine(body = body)).redeem(base, "c", "v")).token

            assertEquals(1893456000L, token.expiresAtEpochSeconds)
        }

    /** An unreadable expiry must not be read as "long expired", or every call would refresh. */
    @Test
    fun anUnreadableExpiryIsUnknownNotExpired() =
        runTest {
            val body = """{"access_token":"a","refresh_token":"r","expires_at":{"weird":true}}"""

            val token = assertIs<TokenResult.Success>(exchange(engine(body = body)).redeem(base, "c", "v")).token

            assertNull(token.expiresAtEpochSeconds)
            assertFalse(token.needsRefresh(nowEpochSeconds = 1_893_456_000L))
        }

    @Test
    fun aFourHundredMeansTheCodeWasRejected() =
        runTest {
            val result =
                exchange(engine(HttpStatusCode.BadRequest, """{"detail":"Invalid or expired authorization code."}"""))
                    .redeem(base, "c", "v")

            assertIs<TokenResult.InvalidCode>(result)
            assertEquals(1, requests, "a consumed code must not be retried")
        }

    @Test
    fun aFourOhOneMeansTheRefreshTokenIsDead() =
        runTest {
            // The gateway's body carries a longer detail; only the status drives the mapping.
            val body = """{"error":"session_expired","detail":"expired"}"""

            val result = exchange(engine(HttpStatusCode.Unauthorized, body)).refresh(base, "rt-1", "nous")

            assertIs<TokenResult.SessionExpired>(result)
        }

    @Test
    fun aFiveOhThreeIsRetryableNotFatal() =
        runTest {
            val service = engine(HttpStatusCode.ServiceUnavailable, """{"detail":"unreachable"}""")

            val result = exchange(service).refresh(base, "rt-1", "nous")

            assertIs<TokenResult.Retryable>(result)
        }

    @Test
    fun anUnreadableBodyIsRetryable() =
        runTest {
            val result = exchange(engine(body = "<html>proxy error</html>")).redeem(base, "c", "v")

            assertIs<TokenResult.Retryable>(result)
        }

    @Test
    fun aBodyMissingTheRefreshTokenIsRetryableNotSuccess() =
        runTest {
            val result = exchange(engine(body = """{"access_token":"a"}""")).redeem(base, "c", "v")

            assertIs<TokenResult.Retryable>(result)
        }

    @Test
    fun refreshPostsTheRefreshTokenAndProvider() =
        runTest {
            var seenUrl = ""
            var seenBody = ""

            exchange(
                engine(body = successBody) { request ->
                    seenUrl = request.url.toString()
                    seenBody = (request.body as TextContent).text
                },
            ).refresh(base, refreshToken = "rt-1", provider = "nous")

            assertEquals("http://127.0.0.1:9119/auth/native/refresh", seenUrl)
            assertTrue(seenBody.contains("\"refresh_token\":\"rt-1\""), "body was $seenBody")
            assertTrue(seenBody.contains("\"provider\":\"nous\""), "body was $seenBody")
        }
}
