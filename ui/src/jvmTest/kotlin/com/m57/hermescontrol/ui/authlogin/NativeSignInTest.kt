package com.m57.hermescontrol.ui.authlogin

import com.m57.hermescontrol.data.local.NativeTokenStore
import com.m57.hermescontrol.data.remote.HermesHttpConfig
import com.m57.hermescontrol.data.remote.createHermesHttpClient
import com.m57.hermescontrol.data.security.SecretStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The chain end to end, with the browser played by a thread that follows the URL it was handed.
 *
 * That is the point of these tests: the authorize URL, the listener's port and the PKCE challenge
 * are three separate pieces of state that only agree if they were produced from the same flow. A
 * test that stubbed the listener would assert nothing about that agreement.
 */
class NativeSignInTest {
    private val base = "http://127.0.0.1:9119"
    private val secrets = FakeSecretStore()
    private val store = NativeTokenStore(secrets)
    private var openedUrl = ""
    private var redeemBody = ""

    private val successBody =
        """{"access_token":"at-1","refresh_token":"rt-1","expires_at":1893456000,"provider":"nous"}"""

    private fun signIn(
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String = successBody,
        timeoutMillis: Long = 5_000,
    ): NativeSignIn {
        val engine =
            MockEngine { request ->
                redeemBody = (request.body as? TextContent)?.text.orEmpty()
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        return NativeSignIn(
            exchange =
                NativeTokenExchange(
                    createHermesHttpClient(engine, HermesHttpConfig.Default.copy(maxRetries = 0)),
                ),
            store = store,
            timeoutMillis = timeoutMillis,
        )
    }

    /** Acts as the browser: read the redirect the URL names, then call it back. */
    private fun browser(
        code: String? = "the-code",
        error: String? = null,
    ): (String) -> Unit =
        { url ->
            openedUrl = url
            val redirectUri = Url(url).parameters["redirect_uri"].orEmpty()
            val state = Url(url).parameters["state"].orEmpty()
            val query = if (error != null) "error=$error&state=$state" else "code=$code&state=$state"
            thread(isDaemon = true) {
                val connection = URL("$redirectUri?$query").openConnection() as HttpURLConnection
                connection.connectTimeout = 2_000
                connection.readTimeout = 2_000
                runCatching { connection.responseCode }
                connection.disconnect()
            }
        }

    /**
     * The assertion that matters: the verifier sent to the gateway hashes to the challenge that
     * went into the authorize URL. If those two ever disagree, every real sign-in fails at the
     * final step with a generic 400.
     */
    @Test
    fun theOpenedUrlReachesTheListenerAndTheVerifierMatchesTheChallenge() =
        runBlocking {
            val result = signIn().signIn(base, openBrowser = browser())

            assertIs<NativeSignInResult.Success>(result)
            assertEquals("at-1", result.token.accessToken)

            val challenge = Url(openedUrl).parameters["code_challenge"]
            val verifier = Json.parseToJsonElement(redeemBody).jsonObject["code_verifier"]?.jsonPrimitive?.content
            assertEquals(challenge, Pkce.challengeFor(assertNotNull(verifier)))
        }

    @Test
    fun theTokenIsPersistedOnSuccess() =
        runBlocking {
            signIn().signIn(base, openBrowser = browser())

            assertEquals("at-1", assertNotNull(store.load()).accessToken)
        }

    @Test
    fun theCodeIsSentToTheGateway() =
        runBlocking {
            signIn().signIn(base, openBrowser = browser(code = "the-code"))

            assertTrue(redeemBody.contains("\"code\":\"the-code\""), "body was $redeemBody")
        }

    @Test
    fun aClosedTabIsReportedAsCancelled() {
        runBlocking {
            val result = signIn(timeoutMillis = 300).signIn(base, openBrowser = {})

            assertIs<NativeSignInResult.Cancelled>(result)
        }
    }

    @Test
    fun aProviderRefusalIsReportedAsFailed() =
        runBlocking {
            val result = signIn().signIn(base, openBrowser = browser(code = null, error = "access_denied"))

            assertEquals("access_denied", assertIs<NativeSignInResult.Failed>(result).reason)
        }

    /**
     * A rejected code must not leave a token behind: the store is what the app trusts on launch,
     * so persisting nothing is the difference between "sign in again" and "authenticated as a
     * token the gateway already refused".
     */
    @Test
    fun aRejectedCodePersistsNoToken() =
        runBlocking {
            val result =
                signIn(status = HttpStatusCode.BadRequest, body = """{"detail":"invalid"}""")
                    .signIn(base, openBrowser = browser())

            assertIs<NativeSignInResult.Failed>(result)
            assertNull(store.load())
        }

    @Test
    fun aPlatformWithoutLoopbackIsReportedAsUnsupported() {
        runBlocking {
            val signIn =
                NativeSignIn(
                    exchange = NativeTokenExchange(createHermesHttpClient(MockEngine { error("must not call") })),
                    store = store,
                    openListener = { throw UnsupportedOperationException("browser") },
                )

            assertIs<NativeSignInResult.Unsupported>(signIn.signIn(base, openBrowser = { error("must not open") }))
        }
    }

    private class FakeSecretStore : SecretStore {
        val values = mutableMapOf<String, String>()

        override fun getString(logicalKey: String): String? = values[logicalKey]

        override fun putString(
            logicalKey: String,
            value: String?,
        ) {
            if (value == null) values.remove(logicalKey) else values[logicalKey] = value
        }

        override fun deletePrefix(prefix: String) {
            values.keys.filter { it.startsWith(prefix) }.forEach(values::remove)
        }
    }
}
