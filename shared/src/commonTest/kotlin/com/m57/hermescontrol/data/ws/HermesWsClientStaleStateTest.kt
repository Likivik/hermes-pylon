package com.m57.hermescontrol.data.ws

import com.m57.hermescontrol.data.remote.ServerEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val WS_URL = "ws://127.0.0.1:19119/api/ws"

/**
 * Regression tests for the failure class that cost a full evening on the web build:
 * a connection state outlives the reason for it, so `connect()` refuses to run and the
 * UI sits on "Reconnecting..." while every REST call still returns 200.
 *
 * `connect()` is right to refuse from `AUTH_EXPIRED` — a socket is not the answer to a
 * rejected credential. The bug was that nothing cleared it after a *verified* session,
 * and that a `CONNECTING` left behind by an attempt which never produced a socket did
 * the same. That second shape is what the web build hit: gated mode needs a ticket, the
 * ticket needs an HTTP client, the client was never installed, so every attempt ended
 * in flight with no socket — and stayed there.
 *
 * These tests live in `commonTest` on purpose: the same file executes on the JVM and in
 * the browser (`wasmJsBrowserTest`), which is the only way a platform difference in this
 * state machine shows up before users hit it. `runBlocking` does not exist on wasm, so
 * waits go through [Dispatchers.Default] with real time — virtual time inside `runTest`
 * cannot observe work launched on the client's own scope.
 */
class HermesWsClientStaleStateTest {
    /** Loopback mode: the client has a token, so it dials straight away. */
    private class LoopbackAuth : WsAuthConfig {
        override fun getSelectedProfileId(): String = "stale-state"

        override fun isGatedMode(): Boolean = false

        override fun isAutoReconnect(): Boolean = false

        override fun wsUrl(): String = WS_URL

        override fun wsUrlWithCredential(
            credential: String,
            authParameter: String,
        ): String = "$WS_URL?$authParameter=$credential"

        override fun endpointForBuild(): ServerEndpoint = error("not used: loopback mode")
    }

    /**
     * Gated mode where the ticket can never be minted — the web failure, reproduced.
     * Reaching for an endpoint throws, which is what a missing seam amounts to: no way
     * to get a ticket, therefore no socket.
     */
    private class UnminatableGatedAuth : WsAuthConfig {
        override fun getSelectedProfileId(): String = "stale-state"

        override fun isGatedMode(): Boolean = true

        override fun isAutoReconnect(): Boolean = false

        override fun wsUrl(): String = WS_URL

        override fun wsUrlWithCredential(
            credential: String,
            authParameter: String,
        ): String = "$WS_URL?$authParameter=$credential"

        override fun endpointForBuild(): ServerEndpoint = error("no ticket source")
    }

    private fun install(auth: WsAuthConfig): RecordingWsTransport {
        val transport = RecordingWsTransport()
        HermesWsClient.resetForTest()
        HermesWsClient.installAuth(auth)
        HermesWsClient.installTransport(transport)
        return transport
    }

    /** Real-time wait: the client works on its own scope, which virtual time cannot drive. */
    private suspend fun settle(millis: Long = 400) {
        withContext(Dispatchers.Default) { delay(millis) }
    }

    private suspend fun awaitFirstDial(
        transport: RecordingWsTransport,
        timeoutMillis: Long = 5_000,
    ) {
        withContext(Dispatchers.Default) {
            withTimeout(timeoutMillis) { transport.firstConnect.await() }
        }
    }

    @Test
    fun `loopback mode dials on the first connect`() =
        runTest {
            val transport = install(LoopbackAuth())

            HermesWsClient.connect()
            awaitFirstDial(transport)

            assertEquals(1, transport.connectedUrls.size, "the first connect dials exactly once")
        }

    @Test
    fun `a second connect while one is in flight is refused`() =
        runTest {
            val transport = install(LoopbackAuth())

            HermesWsClient.connect()
            awaitFirstDial(transport)
            HermesWsClient.connect()
            settle(200)

            assertEquals(
                1,
                transport.connectedUrls.size,
                "re-entrant connect must not open a second socket",
            )
        }

    /**
     * The stuck shape: an attempt that can never mint a ticket must not dial, and the
     * client must report a wiring fault rather than a transient one — `RECONNECTING`
     * here would promise a retry that cannot succeed and rebuild the silent banner.
     */
    @Test
    fun `an attempt that cannot mint a ticket never dials`() =
        runTest {
            val transport = install(UnminatableGatedAuth())

            HermesWsClient.connect()
            settle()

            assertTrue(
                transport.connectedUrls.isEmpty(),
                "nothing may dial before a ticket is minted",
            )
            assertEquals(
                ConnectionStatus.DISCONNECTED,
                HermesWsClient.connectionStatus.value,
                "a missing HTTP client is a wiring bug: no reconnect, no banner, an error log",
            )
        }

    @Test
    fun `clearing is a no-op when there is nothing stale`() =
        runTest {
            install(LoopbackAuth())

            HermesWsClient.clearStaleAuthExpiry()

            assertEquals(
                ConnectionStatus.DISCONNECTED,
                HermesWsClient.connectionStatus.value,
                "an idle client stays idle",
            )
        }
}
