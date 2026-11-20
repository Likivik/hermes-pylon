package com.m57.hermescontrol.ui.chat

import com.m57.hermescontrol.data.ws.ConnectionBinding
import com.m57.hermescontrol.data.ws.ConnectionStatus
import com.m57.hermescontrol.data.ws.HermesWsClient
import com.m57.hermescontrol.data.ws.PrivilegedRequestBinding
import com.m57.hermescontrol.data.ws.ServerRequestBinding
import com.m57.hermescontrol.data.ws.WsEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonElement

/**
 * The chat screen's view of the websocket, as a seam instead of a global.
 *
 * `HermesWsClient` is a Kotlin `object`, so the ViewModel could only be tested by mocking a static —
 * which is why `ChatViewModelTest` is 5,736 lines of static expectations and why a second gateway
 * instance cannot be reasoned about at all. This interface names the 15 members the screen actually
 * uses; production delegates to the singleton, tests pass a fake.
 *
 * No behaviour lives here. If a method needs logic, it belongs in the transport, not the adapter.
 */
interface ChatTransport {
    val events: SharedFlow<WsEvent>

    val connectionStatus: StateFlow<ConnectionStatus>

    fun connect()

    fun disconnect(clearPendingMessages: Boolean = false)

    fun send(
        method: String,
        params: Map<String, Any> = emptyMap(),
        onSent: ((String) -> Unit)? = null,
    ): String

    fun request(
        method: String,
        params: Map<String, Any> = emptyMap(),
        timeoutMs: Long = HermesWsClient.REQUEST_TIMEOUT_MS,
    ): CompletableDeferred<Any?>

    fun privilegedRequest(
        method: String,
        binding: PrivilegedRequestBinding,
        params: Map<String, String> = emptyMap(),
    ): CompletableDeferred<Any?>

    fun respondToServerRequest(
        binding: ServerRequestBinding,
        result: JsonElement,
    ): Boolean

    fun respondToClarify(
        sessionId: String,
        clarifyRequestId: String,
        questionId: String?,
        answer: String,
        sourceProfileId: String,
        sourceConnectionGeneration: Int,
    ): Boolean

    fun sendMessage(
        sessionId: String,
        text: String,
        onSent: ((String) -> Unit)? = null,
        queued: Boolean = false,
    ): String

    fun sendMessageForConnection(
        binding: ConnectionBinding,
        sessionId: String,
        text: String,
        onSent: ((String) -> Unit)? = null,
    ): Boolean

    fun sendRedirectForConnection(
        binding: ConnectionBinding,
        sessionId: String,
        text: String,
        onSent: ((String) -> Unit)? = null,
    ): Boolean

    fun connectionBinding(expectedProfileId: String): ConnectionBinding?

    fun isConnectionBindingCurrent(binding: ConnectionBinding): Boolean

    fun requestForConnection(
        binding: ConnectionBinding,
        method: String,
        params: Map<String, Any> = emptyMap(),
        timeoutMs: Long = HermesWsClient.REQUEST_TIMEOUT_MS,
    ): CompletableDeferred<Any?>

    fun rejectAllPending(
        error: HermesWsClient.HermesRpcException =
            HermesWsClient.HermesRpcException(
                "Connection lost — request cancelled",
            ),
    )
}

/** Production adapter: the singleton, unchanged. */
object DefaultChatTransport : ChatTransport {
    override val events: SharedFlow<WsEvent> get() = HermesWsClient.events

    override val connectionStatus: StateFlow<ConnectionStatus> get() = HermesWsClient.connectionStatus

    override fun connect() = HermesWsClient.connect()

    override fun disconnect(clearPendingMessages: Boolean) = HermesWsClient.disconnect(clearPendingMessages)

    override fun send(
        method: String,
        params: Map<String, Any>,
        onSent: ((String) -> Unit)?,
    ): String = HermesWsClient.send(method, params, onSent)

    override fun request(
        method: String,
        params: Map<String, Any>,
        timeoutMs: Long,
    ): CompletableDeferred<Any?> = HermesWsClient.request(method, params, timeoutMs)

    override fun privilegedRequest(
        method: String,
        binding: PrivilegedRequestBinding,
        params: Map<String, String>,
    ): CompletableDeferred<Any?> = HermesWsClient.privilegedRequest(method, binding, params)

    override fun respondToServerRequest(
        binding: ServerRequestBinding,
        result: JsonElement,
    ): Boolean = HermesWsClient.respondToServerRequest(binding, result)

    override fun respondToClarify(
        sessionId: String,
        clarifyRequestId: String,
        questionId: String?,
        answer: String,
        sourceProfileId: String,
        sourceConnectionGeneration: Int,
    ): Boolean =
        HermesWsClient.respondToClarify(
            sessionId = sessionId,
            clarifyRequestId = clarifyRequestId,
            questionId = questionId,
            answer = answer,
            sourceProfileId = sourceProfileId,
            sourceConnectionGeneration = sourceConnectionGeneration,
        )

    override fun sendMessage(
        sessionId: String,
        text: String,
        onSent: ((String) -> Unit)?,
        queued: Boolean,
    ): String = HermesWsClient.sendMessage(sessionId, text, onSent, queued)

    override fun sendMessageForConnection(
        binding: ConnectionBinding,
        sessionId: String,
        text: String,
        onSent: ((String) -> Unit)?,
    ): Boolean = HermesWsClient.sendMessageForConnection(binding, sessionId, text, onSent)

    override fun sendRedirectForConnection(
        binding: ConnectionBinding,
        sessionId: String,
        text: String,
        onSent: ((String) -> Unit)?,
    ): Boolean = HermesWsClient.sendRedirectForConnection(binding, sessionId, text, onSent)

    override fun connectionBinding(expectedProfileId: String): ConnectionBinding? =
        HermesWsClient.connectionBinding(expectedProfileId)

    override fun isConnectionBindingCurrent(binding: ConnectionBinding): Boolean =
        HermesWsClient.isConnectionBindingCurrent(binding)

    override fun requestForConnection(
        binding: ConnectionBinding,
        method: String,
        params: Map<String, Any>,
        timeoutMs: Long,
    ): CompletableDeferred<Any?> = HermesWsClient.requestForConnection(binding, method, params, timeoutMs)

    override fun rejectAllPending(error: HermesWsClient.HermesRpcException) = HermesWsClient.rejectAllPending(error)
}
