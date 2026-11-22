package com.m57.hermescontrol.data.remote

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Password login is the one call made before a session exists, and it is the only place the app still sends a
 * hand-built JSON body. These cases freeze today's behaviour so the move into [HermesGatewayApi] cannot drift.
 *
 * The status is the result: the app switches on 401 and 403 to explain the failure, and never reads a body. The
 * timeout is short on purpose — a login that hangs for the default 100 s is indistinguishable from a frozen app.
 */
class LoginCallTest {
    private val baseUrl = ServerEndpoint.parse("https://gw.example/").baseUrl

    private fun api(engine: MockEngine) = HermesGatewayApi(createHermesHttpClient(engine), baseUrl)

    @Test
    fun postsTheJsonBodyToTheLoginRouteAndReportsTheStatus() =
        runTest {
            var method: HttpMethod? = null
            var path: String? = null
            var contentType: String? = null
            var body: String? = null
            val engine =
                MockEngine { request ->
                    method = request.method
                    path = request.url.encodedPath
                    contentType = request.body.contentType?.toString()
                    body = request.body.toByteArray().decodeToString()
                    respond(
                        content = "",
                        status = HttpStatusCode.NoContent,
                        headers = headersOf("Content-Type", "application/json"),
                    )
                }

            val result = api(engine).loginWithPassword("""{"password":"hunter2"}""", timeoutMillis = 10_000)

            assertEquals(HttpMethod.Post, method)
            assertEquals("/auth/password-login", path)
            assertTrue(contentType!!.startsWith(ContentType.Application.Json.toString()), "got $contentType")
            assertEquals("""{"password":"hunter2"}""", body)
            assertEquals(204, result.code)
            assertTrue(result.isSuccessful)
        }

    @Test
    fun aWrongPasswordIsAStatusNotAnException() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        content = """{"detail":"invalid password"}""",
                        status = HttpStatusCode.Unauthorized,
                        headers = headersOf("Content-Type", "application/json"),
                    )
                }

            val result = api(engine).loginWithPassword("""{"password":"nope"}""")

            assertEquals(401, result.code)
            assertFalse(result.isSuccessful)
        }

    @Test
    fun aForbiddenLoginIsAlsoJustAStatus() =
        runTest {
            val engine = MockEngine { respond("", HttpStatusCode.Forbidden) }

            assertEquals(403, api(engine).loginWithPassword("""{"password":"nope"}""").code)
        }
}
