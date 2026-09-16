package com.m57.hermescontrol.e2e

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

/**
 * Bridges [ScriptedGateway] (pure scripted choreography) to a real socket via
 * MockWebServer's WebSocket upgrade — production-faithful RealWebSocket on
 * both sides, per research (okhttp5 mockwebserver3 webSocketUpgrade).
 *
 * androidTest runs in the app's process, so the emulator's loopback
 * (127.0.0.1) is correct — no 10.0.2.2 bridge needed.
 */
class ScriptedGatewayServer(
    val gateway: ScriptedGateway,
) {
    val server = MockWebServer()
    private val client = OkHttpClient()
    private var appSocket: WebSocket? = null

    /** Signals the app's WS is open and the upgrade handshake completed. */
    private val opened = CountDownLatch(1)

    private val serverListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            appSocket = webSocket
            opened.countDown()
            // Real gateway always emits gateway.ready immediately after accept
            // (tui_gateway/ws.py:311). Keep the mock contract-identical.
            webSocket.send(
                """{"jsonrpc":"2.0","method":"event","params":{""" +
                    """"type":"gateway.ready","payload":{"skin":"default","change_events":true}}}""",
            )
            // Anything the gateway script pushes asynchronously gets delivered
            // from a background thread — the scripted push channel drives it.
            Thread {
                while (true) {
                    val frame = gateway.pushChannel.tryReceive().getOrNull() ?: break
                    webSocket.send(frame)
                }
            }.start()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            // Client frame in → script decides the reply frame (or none).
            gateway.onClientFrame(text)?.let { reply ->
                webSocket.send(reply)
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            // Client disconnect (teardown) surfaces here; not a test failure.
        }
    }

    fun start(): String {
        server.start(java.net.InetAddress.getByName("127.0.0.1"), 0)
        server.enqueue(MockResponse().withWebSocketUpgrade(serverListener))
        return "ws://127.0.0.1:${server.port}/ws"
    }

    /** For the app-under-test: connect the REAL client stack to the mock. */
    fun connectRealClient(wsUrl: String): WebSocket =
        client.newWebSocket(
            Request.Builder().url(wsUrl).build(),
            object : WebSocketListener() {},
        )

    fun awaitOpen(timeoutMs: Long = 5000) {
        check(opened.await(timeoutMs, TimeUnit.MILLISECONDS)) { "gateway WS never opened" }
    }

    fun shutdown() {
        appSocket?.close(1000, "test done")
        server.shutdown()
    }
}
