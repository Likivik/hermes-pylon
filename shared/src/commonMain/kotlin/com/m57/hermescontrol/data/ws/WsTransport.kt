package com.m57.hermescontrol.data.ws

/**
 * WebSocket transport seam.
 *
 * This file deliberately imports nothing from okhttp, java, javax, android, or
 * any other platform-specific module. `:shared` is the protocol layer; the
 * concrete `KtorWsTransport` lives next to this file in `commonMain` and reaches
 * the platform engine only through Ktor's per-target engine resolution.
 *
 * The seam is intentionally minimal — three operations per socket (send, close,
 * cancel) and five listener callbacks. Anything more sophisticated belongs on
 * the implementation side.
 *
 * Why [WsListener] rather than a `Flow<WsEvent>`: the existing
 * `HermesWsClient` uses identity guards (`socket !== HermesWsClient.webSocket`)
 * to drop stale callbacks from superseded connections. Identity is preserved by
 * wrapping the underlying okhttp socket once per `connect()` call, so the
 * callback's [WsSocket] reference stays constant for the lifetime of that
 * connection.
 */
interface WsSocket {
    fun send(text: String): Boolean

    fun close(
        code: Int,
        reason: String,
    ): Boolean

    fun cancel()

    /**
     * Whether the transport still holds outbound frames it accepted but has
     * not yet written.
     *
     * The recovery path in [HermesWsClient.recoverRejectedSocket] branches on
     * this: when the transport refuses a new frame, an empty outbound buffer
     * means we can cancel immediately and reconnect, while pending frames mean
     * we give the transport a bounded grace period to drain what it already
     * accepted. That path only needs the yes/no answer, so the seam asks for
     * one rather than exposing a byte count callers must interpret.
     *
     * Default is false so transports that cannot report a backlog behave as if
     * the buffer were always empty (immediate cancel + reconnect).
     * [KtorWsTransport] overrides it with the number of frames it accepted but
     * has not yet handed to the engine.
     */
    fun hasPendingWrites(): Boolean = false
}

interface WsListener {
    fun onOpen(socket: WsSocket)

    fun onText(
        socket: WsSocket,
        text: String,
    )

    fun onClosing(
        socket: WsSocket,
        code: Int,
        reason: String,
    )

    fun onClosed(
        socket: WsSocket,
        code: Int,
        reason: String,
    )

    /**
     * @param statusCode HTTP status from the upgrade response, when the failure
     *   is associated with one (e.g. 401 -> re-auth instead of reconnect). Null
     *   when the failure is purely transport-level — no `Response` was ever
     *   produced. This is the seam equivalent of `okhttp3.Response?.code` and
     *   exists so `:shared` never has to name an okhttp type.
     */
    fun onFailure(
        socket: WsSocket,
        cause: Throwable,
        statusCode: Int?,
    )
}

interface WsTransport {
    fun connect(
        url: String,
        listener: WsListener,
    ): WsSocket
}
