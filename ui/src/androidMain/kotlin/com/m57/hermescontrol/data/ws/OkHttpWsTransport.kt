package com.m57.hermescontrol.data.ws

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * OkHttp-backed [WsTransport]. The only production implementation: every
 * other module sees [WsSocket] / [WsListener] / [WsTransport] and never
 * imports an okhttp type.
 *
 * Identity stability: a single [OkHttpWsSocket] instance is created per
 * `connect()` call and reused for every callback on that connection, so
 * `HermesWsClient`'s `socket !== HermesWsClient.webSocket` identity guards
 * continue to drop stale callbacks from superseded connections.
 */
class OkHttpWsTransport(
    private val client: OkHttpClient,
) : WsTransport {
    override fun connect(
        url: String,
        listener: WsListener,
    ): WsSocket {
        val request = Request.Builder().url(url).build()
        // The wrapping socket is created up-front so all five callback
        // methods see the same instance — the identity HermesWsClient guards
        // against stale callbacks.
        lateinit var wrapper: OkHttpWsSocket
        val bridge =
            object : WebSocketListener() {
                override fun onOpen(
                    webSocket: WebSocket,
                    response: Response,
                ) {
                    listener.onOpen(wrapper)
                }

                override fun onMessage(
                    webSocket: WebSocket,
                    text: String,
                ) {
                    listener.onText(wrapper, text)
                }

                override fun onClosing(
                    webSocket: WebSocket,
                    code: Int,
                    reason: String,
                ) {
                    listener.onClosing(wrapper, code, reason)
                }

                override fun onClosed(
                    webSocket: WebSocket,
                    code: Int,
                    reason: String,
                ) {
                    listener.onClosed(wrapper, code, reason)
                }

                override fun onFailure(
                    webSocket: WebSocket,
                    t: Throwable,
                    response: Response?,
                ) {
                    listener.onFailure(wrapper, t, response?.code)
                }
            }
        wrapper = OkHttpWsSocket(client.newWebSocket(request, bridge))
        return wrapper
    }
}

private class OkHttpWsSocket(
    private val socket: WebSocket,
) : WsSocket {
    override fun send(text: String): Boolean = socket.send(text)

    override fun close(
        code: Int,
        reason: String,
    ): Boolean = socket.close(code, reason)

    override fun cancel() {
        socket.cancel()
    }

    override fun hasPendingWrites(): Boolean = socket.queueSize() > 0L
}
