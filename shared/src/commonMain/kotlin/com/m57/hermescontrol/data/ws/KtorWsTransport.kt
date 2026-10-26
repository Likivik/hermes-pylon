package com.m57.hermescontrol.data.ws

import com.m57.hermescontrol.concurrent.ioDispatcher
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.pluginOrNull
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.url
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readReason
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

/**
 * Ktor-backed [WsTransport] — the single implementation for every target.
 *
 * It lives in `commonMain` so Android, the JVM console and wasmJs all speak the
 * chat stream through one WebSocket stack. The engine is the platform default
 * resolved by `ktor-client-engine-defaults` (OkHttp on Android, CIO on the JVM,
 * the Js engine in the browser), which is why this file names no engine type.
 *
 * Identity stability: exactly one [KtorWsSocket] is created per [connect] call
 * and every callback on that connection receives it, so `HermesWsClient`'s
 * `socket !== HermesWsClient.webSocket` guards keep dropping callbacks from
 * superseded connections.
 *
 * Connect is asynchronous by necessity (a browser cannot block on a handshake),
 * so [connect] returns the socket immediately and the handshake, the frame
 * reader and the close/failure notification all run on [ioDispatcher]. The
 * observable contract matches the previous OkHttp transport:
 * - `onOpen` once, after a successful upgrade;
 * - `onText` per text frame, in order;
 * - `onClosing` when the peer's close frame arrives;
 * - `onClosed` once when the session ends normally (either side initiated);
 * - `onFailure` when the upgrade or the session fails, including after
 *   [WsSocket.cancel] — the recovery paths in `HermesWsClient` schedule their
 *   own reconnect from that terminal callback, so suppressing it would strand a
 *   cancelled socket in `RECONNECTING`.
 * Exactly one of `onClosed`/`onFailure` is delivered per connection.
 */
class KtorWsTransport(
    private val client: HttpClient = defaultWsClient(),
) : WsTransport {
    /**
     * The caller usually hands in the app's gateway client — the one whose
     * engine carries the cookie jar and authenticator — and that client is built
     * by [installHermesPlugins], which does not install WebSockets. Derive a
     * child client that keeps the same engine (so cookies still flow) and only
     * adds the websocket plugin. `config {}` shares the engine, so this client
     * must never be closed: closing it would tear down the shared dispatcher.
     */
    private val socketClient: HttpClient by lazy {
        if (client.pluginOrNull(WebSockets) != null) client else client.config { install(WebSockets) }
    }

    override fun connect(
        url: String,
        listener: WsListener,
    ): WsSocket {
        val socket = KtorWsSocket(socketClient, url, listener)
        socket.start()
        return socket
    }

    companion object {
        /**
         * Client for the socket stream: the WebSockets plugin plus an
         * effectively infinite request/socket timeout, mirroring the unbounded
         * read timeout the OkHttp websocket client used. A finite timeout would
         * tear down an idle-but-healthy session between heartbeats.
         */
        fun defaultWsClient(): HttpClient =
            HttpClient {
                install(WebSockets)
                install(HttpTimeout) {
                    requestTimeoutMillis = Long.MAX_VALUE
                    socketTimeoutMillis = Long.MAX_VALUE
                }
            }
    }
}

/**
 * Transport-side frame that could not be written or whose session was
 * cancelled. Deliberately a plain [Exception] (`java.io.IOException` is not
 * available in `commonMain`); its message stays "Socket closed" so it reads the
 * same in logs as the OkHttp equivalent.
 */
private class WsSocketClosedException : Exception("Socket closed")

private class KtorWsSocket(
    private val client: HttpClient,
    private val url: String,
    private val listener: WsListener,
) : WsSocket {
    private val scope = CoroutineScope(ioDispatcher + SupervisorJob())

    /**
     * Frames this transport accepted but has not yet handed to the engine.
     * Mirrors the OkHttp transport's `queueSize() > 0`, which
     * `recoverRejectedSocket` uses to decide between an immediate cancel and a
     * bounded drain.
     */
    private val pendingWrites = atomic(0)
    private val terminal = atomic(false)
    private val abrupt = atomic(false)
    private val closeRequested = atomic(false)

    /**
     * Outbound buffer between the synchronous [send] contract and the
     * suspending engine write. Bounded so a congested socket refuses frames
     * (returning `false` makes `HermesWsClient` queue and recover) instead of
     * growing without limit.
     */
    private val outbound = Channel<Frame.Text>(capacity = OUTBOUND_QUEUE_CAPACITY)

    @Volatile
    private var job: Job? = null

    @Volatile
    private var session: DefaultClientWebSocketSession? = null

    @Volatile
    private var requestedCloseCode: Int? = null

    @Volatile
    private var requestedCloseReason: String = ""

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun start() {
        job =
            scope.launch {
                var receivedClose: CloseReason? = null
                var closingReported = false
                var failure: Throwable? = null
                var writer: Job? = null
                try {
                    val established = client.webSocketSession { url(this@KtorWsSocket.url) }
                    session = established
                    if (closeRequested.value || abrupt.value) {
                        // Closed while the handshake was still in flight: there is
                        // no listener session to report, so just drop it.
                        established.cancel()
                    } else {
                        listener.onOpen(this@KtorWsSocket)
                        writer = launch { writeLoop(established) }
                        for (frame in established.incoming) {
                            when (frame) {
                                is Frame.Text -> listener.onText(this@KtorWsSocket, frame.readText())
                                is Frame.Close -> {
                                    receivedClose = frame.readReason()
                                    val reason = receivedClose
                                    listener.onClosing(
                                        this@KtorWsSocket,
                                        reason?.code?.toInt() ?: NORMAL_CLOSE,
                                        reason?.message ?: "",
                                    )
                                    closingReported = true
                                }
                                else -> Unit // Binary frames are not part of the JSON-RPC protocol.
                            }
                        }
                        // The peer closed the socket: the engine ends the incoming
                        // channel, but it does NOT push the peer's close code into
                        // it. Ktor carries that code on the session's `closeReason`
                        // deferred instead, so read it here — otherwise a close
                        // code like 4401 (auth: ticket rejected) would surface as
                        // 1000 and the client's fresh-ticket retry would never
                        // fire.
                        val deferredClose = established.closeReason
                        if (receivedClose == null && deferredClose.isCompleted) {
                            receivedClose = deferredClose.getCompleted()
                        }
                        val resolvedClose = receivedClose
                        if (resolvedClose != null && !closingReported) {
                            closingReported = true
                            listener.onClosing(
                                this@KtorWsSocket,
                                resolvedClose.code.toInt(),
                                resolvedClose.message,
                            )
                        }
                    }
                } catch (cancellation: CancellationException) {
                    // A scope-level cancellation is only ever ours (cancel() /
                    // close() before open). Anything else is a real coroutine
                    // cancellation and must propagate.
                    if (!abrupt.value && !closeRequested.value && !terminal.value) throw cancellation
                } catch (t: Throwable) {
                    failure = t
                } finally {
                    // The writer is a child of this coroutine; cancelling it here
                    // keeps structured concurrency from waiting on it forever.
                    writer?.cancel()
                    outbound.close()
                    terminate(failure, receivedClose)
                }
            }
    }

    private suspend fun writeLoop(established: DefaultClientWebSocketSession) {
        for (frame in outbound) {
            try {
                established.send(frame)
            } catch (t: Throwable) {
                // The engine refused a frame: the session is unusable. Abort it so
                // the reader terminates and reports the failure.
                established.cancel()
                break
            } finally {
                pendingWrites.decrementAndGet()
            }
        }
    }

    override fun send(text: String): Boolean {
        if (terminal.value || closeRequested.value || abrupt.value) return false
        if (session == null) return false
        pendingWrites.incrementAndGet()
        if (outbound.trySend(Frame.Text(text)).isFailure) {
            pendingWrites.decrementAndGet()
            return false
        }
        return true
    }

    override fun close(
        code: Int,
        reason: String,
    ): Boolean {
        if (terminal.value) return false
        if (!closeRequested.compareAndSet(false, true)) return false
        requestedCloseCode = code
        requestedCloseReason = reason
        val established = session
        if (established == null) {
            // Still handshaking: drop the connection; the terminal callback is
            // delivered from the connect coroutine's finally block.
            job?.cancel()
            return true
        }
        scope.launch {
            // The peer may already have sent its close frame; echoing twice is
            // not an error, so a refused close is not reported as a failure.
            runCatching { established.close(CloseReason(code.toShort(), reason)) }
        }
        return true
    }

    override fun cancel() {
        abrupt.value = true
        val established = session
        if (established != null) {
            established.cancel()
        } else {
            job?.cancel()
        }
    }

    override fun hasPendingWrites(): Boolean = pendingWrites.value > 0

    /**
     * Deliver the single terminal callback for this connection, then release the
     * scope. Guarded so a late reader/writer exit cannot double-report.
     */
    private fun terminate(
        failure: Throwable?,
        receivedClose: CloseReason?,
    ) {
        if (!terminal.compareAndSet(false, true)) return
        session = null
        try {
            when {
                abrupt.value -> listener.onFailure(this, WsSocketClosedException(), null)
                failure != null -> listener.onFailure(this, failure, failure.handshakeStatusCode())
                else -> {
                    val code = receivedClose?.code?.toInt() ?: requestedCloseCode ?: NORMAL_CLOSE
                    val reason = receivedClose?.message ?: requestedCloseReason
                    listener.onClosed(this, code, reason)
                }
            }
        } finally {
            scope.cancel()
        }
    }

    private companion object {
        const val OUTBOUND_QUEUE_CAPACITY = 64
        const val NORMAL_CLOSE = 1000
    }
}

/**
 * HTTP status behind a failed upgrade, when there is one.
 *
 * Ktor reports a non-101 handshake as a `WebSocketException` whose message
 * carries the status ("Handshake exception, expected status code 101 but was
 * 401") rather than the response object, so the status is recovered from that
 * message — the same fallback `HermesWsClient.onFailure` already performs for
 * 401. A `ResponseException` (the other shape Ktor can throw) is unwrapped
 * directly. Purely transport-level failures return null, matching the seam's
 * `okhttp3.Response?.code` semantics.
 */
private fun Throwable.handshakeStatusCode(): Int? {
    if (this is ResponseException) return response.status.value
    val message = message ?: return null
    return STATUS_CODE.findAll(message)
        .mapNotNull { it.value.toIntOrNull() }
        .filter { it in 100..599 && it != SWITCHING_PROTOCOLS }
        .lastOrNull()
}

private val STATUS_CODE = Regex("\\d{3}")

private const val SWITCHING_PROTOCOLS = 101
