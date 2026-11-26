package com.m57.hermescontrol.data.local

import com.m57.hermescontrol.data.remote.HermesHttpConfig
import com.m57.hermescontrol.data.remote.createHermesHttpClient
import com.m57.hermescontrol.data.security.AUTH_SECRET_PREFIX
import com.m57.hermescontrol.data.security.SecretStore
import com.m57.hermescontrol.ui.authlogin.NativeToken
import com.m57.hermescontrol.ui.authlogin.NativeTokenExchange
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The refresh rules that matter are not "does it call the endpoint" but "how many times" and
 * "what survives a failure". The gateway rotates the refresh token on every use, so a second
 * needless call turns a working session into a dead one.
 */
class NativeSessionTest {
    private val base = "http://127.0.0.1:9119"
    private val secrets = FakeSecretStore()
    private val store = NativeTokenStore(secrets)
    private var refreshCalls = 0

    private val now = 1_000_000L
    private val fresh = now + 3_600
    private val nearlyExpired = now + 10

    private fun session(
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String = """{"access_token":"at-2","refresh_token":"rt-2","expires_at":9999999999,"provider":"nous"}""",
    ): NativeSession {
        val engine =
            MockEngine {
                refreshCalls += 1
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        return NativeSession(
            exchange =
                NativeTokenExchange(
                    createHermesHttpClient(engine, HermesHttpConfig.Default.copy(maxRetries = 0)),
                ),
            store = store,
            nowEpochSeconds = { now },
        )
    }

    private fun saveToken(expiresAt: Long?) {
        store.save(
            NativeToken(
                accessToken = "at-1",
                refreshToken = "rt-1",
                expiresAtEpochSeconds = expiresAt,
                provider = "nous",
            ),
        )
    }

    @Test
    fun aFreshTokenIsReturnedWithoutCallingTheGateway() =
        runTest {
            saveToken(fresh)

            val token = session().current(base)

            assertEquals("at-1", assertNotNull(token).accessToken)
            assertEquals(0, refreshCalls)
        }

    @Test
    fun aTokenNearExpiryIsRenewedAndTheNewPairIsStored() =
        runTest {
            saveToken(nearlyExpired)

            val token = session().current(base)

            assertEquals("at-2", assertNotNull(token).accessToken)
            assertEquals("rt-2", assertNotNull(store.load()).refreshToken)
            assertEquals(1, refreshCalls)
        }

    /** An unknown expiry cannot be judged, so it is not treated as expired. */
    @Test
    fun anUnknownExpiryIsNotRenewedOnEveryCall() =
        runTest {
            saveToken(null)

            session().current(base)

            assertEquals(0, refreshCalls)
        }

    @Test
    fun aProviderRejectionEndsTheSession() =
        runTest {
            saveToken(nearlyExpired)

            val token =
                session(
                    status = HttpStatusCode.Unauthorized,
                    body = """{"error":"session_expired"}""",
                ).current(base)

            assertNull(token)
            assertNull(store.load())
        }

    /**
     * A 503 means the provider is down, not that the session is over. Dropping the token here
     * would sign the user out during an outage.
     */
    @Test
    fun aProviderOutageKeepsTheHeldToken() =
        runTest {
            saveToken(nearlyExpired)

            val token =
                session(
                    status = HttpStatusCode.ServiceUnavailable,
                    body = """{"detail":"unreachable"}""",
                ).current(base)

            assertEquals("at-1", assertNotNull(token).accessToken)
            assertEquals("rt-1", assertNotNull(store.load()).refreshToken, "the pair must survive the outage")
        }

    /**
     * The whole reason for the lock: two callers in the same tick must produce one rotation.
     * Two rotations in sequence would leave one caller holding a refresh token the gateway has
     * already replaced.
     */
    @Test
    fun concurrentCallersRotateOnce() =
        runTest {
            saveToken(nearlyExpired)
            val session = session()

            val results = listOf(async { session.current(base) }, async { session.current(base) }).awaitAll()

            assertEquals(1, refreshCalls, "one rotation, not one per caller")
            assertEquals(listOf("at-2", "at-2"), results.map { it?.accessToken })
        }

    /** A 401 that arrived despite a token that looked fresh must still force a rotation. */
    @Test
    fun aRejectionForcesARotationEvenWhenTheTokenLooksFresh() =
        runTest {
            saveToken(fresh)

            val token = session().renewAfterRejection(base)

            assertEquals("at-2", assertNotNull(token).accessToken)
            assertEquals(1, refreshCalls)
        }

    @Test
    fun withNothingStoredThereIsNoSession() =
        runTest {
            assertNull(session().current(base))
            assertEquals(0, refreshCalls)
        }

    @Test
    fun forgetClearsTheStoredPair() {
        saveToken(fresh)

        session().forget()

        assertNull(store.load())
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
            values.keys.filter { it.startsWith(AUTH_SECRET_PREFIX) }.forEach(values::remove)
        }
    }
}
