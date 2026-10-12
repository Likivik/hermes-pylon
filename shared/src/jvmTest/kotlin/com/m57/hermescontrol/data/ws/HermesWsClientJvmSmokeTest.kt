package com.m57.hermescontrol.data.ws

import com.m57.hermescontrol.data.remote.ServerEndpoint
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Smoke test: `HermesWsClient` runs off Android.
 *
 * The client lives in `commonMain` and imports no platform API, but until this
 * test existed nothing ever *ran* it anywhere except `:app`'s Android unit
 * tests. Compiling for a target and working on it are different claims, and a
 * console (or web) target needs the second one.
 *
 * Everything the client reaches for is installed here through its seams —
 * [WsAuthConfig] and [WsTransport] — so no Android type is involved.
 */
class HermesWsClientJvmSmokeTest {
    private class FakeAuthConfig : WsAuthConfig {
        override fun getSelectedProfileId(): String = "smoke-profile"

        override fun isGatedMode(): Boolean = false

        override fun isAutoReconnect(): Boolean = false

        override fun wsUrl(): String = WS_URL

        override fun wsUrlWithCredential(
            credential: String,
            authParameter: String,
        ): String = "$WS_URL?$authParameter=$credential"

        override fun endpointForBuild(): ServerEndpoint = error("not used: gated mode is off")
    }

    @Test
    fun `client drives an installed transport on a plain JVM`() {
        val transport = RecordingWsTransport()
        HermesWsClient.installAuth(FakeAuthConfig())
        HermesWsClient.installTransport(transport)

        HermesWsClient.connect()
        try {
            val url = runBlocking { withTimeout(CONNECT_TIMEOUT_MS) { transport.firstConnect.await() } }

            assertEquals(WS_URL, url, "client connected to the wrong URL")
            assertNotNull(HermesWsClient.socketForTest, "connect() published no socket")
            assertSame(transport.socket, HermesWsClient.socketForTest, "published socket is not the transport's")
        } finally {
            HermesWsClient.disconnect(clearPendingMessages = true)
            HermesWsClient.resetForTest()
        }

        val socket = transport.socket
        assertTrue(
            socket.closed != null || socket.cancelled,
            "disconnect() left the socket open: closed=${socket.closed} cancelled=${socket.cancelled}",
        )
    }

    private companion object {
        const val WS_URL = "ws://127.0.0.1:9119/api/ws"
        const val CONNECT_TIMEOUT_MS = 5_000L
    }
}
