package com.m57.hermescontrol.ui.chat.fakes

import com.m57.hermescontrol.data.remote.HermesApi
import com.m57.hermescontrol.data.ws.ConnectionBinding
import com.m57.hermescontrol.data.ws.ConnectionStatus
import com.m57.hermescontrol.data.ws.HermesWsClient
import com.m57.hermescontrol.data.ws.PrivilegedRequestBinding
import com.m57.hermescontrol.data.ws.ServerRequestBinding
import com.m57.hermescontrol.data.ws.WsEvent
import com.m57.hermescontrol.ui.chat.ChatGateway
import com.m57.hermescontrol.ui.chat.ChatTransport
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonElement

/**
 * A `ChatTransport` a test can drive, instead of `mockkObject(HermesWsClient)`.
 *
 * Static mocking was the reason `ChatViewModelTest` is 5,736 lines: every expectation was declared
 * against a global, so a test could not be read without knowing the singleton's surface. This fake
 * exposes only what the view model uses, records what it sent, and lets a test answer with a lambda.
 *
 * Defaults mirror production semantics that tests rely on: `send` and `sendMessage` allocate a
 * monotonically increasing id and invoke the `onSent` callback synchronously.
 */
class FakeChatTransport : ChatTransport {
    val eventFlow = MutableSharedFlow<WsEvent>(extraBufferCapacity = 64)

    val status = MutableStateFlow(ConnectionStatus.DISCONNECTED)

    override val events get() = eventFlow

    override val connectionStatus get() = status

    /** Set by tests that need a binding to exist; `null` means "not connected". */
    var binding: ConnectionBinding? = mockk(relaxed = true)

    var bindingIsCurrent: Boolean = true

    var sendCount = 0
    var messageCount = 0

    /** Every `send` call in order: method, params. */
    val sends = mutableListOf<SendCall>()

    /** Every `sendMessage` call in order. */
    val sentMessages = mutableListOf<SentMessage>()

    /**
     * Every `sendRedirectForConnection` call in order.
     *
     * Separate from [sentMessages] because redirect and submit record identically there, and the send
     * pipeline's whole point is to choose between them.
     */
    val redirects = mutableListOf<SentMessage>()

    /** Every `request` call in order: method, params, timeout. */
    val requests = mutableListOf<RequestCall>()

    var onSend: (method: String, params: Map<String, Any>) -> String = { _, _ -> nextSendId() }

    var onSendMessage: (sessionId: String, text: String, queued: Boolean) -> String =
        { _, _, _ -> nextMessageId() }

    var onRequest: (method: String, params: Map<String, Any>, timeoutMs: Long) -> CompletableDeferred<Any?> =
        { _, _, _ -> CompletableDeferred(Unit) }

    var onPrivilegedRequest:
        (method: String, binding: PrivilegedRequestBinding, params: Map<String, String>) -> CompletableDeferred<Any?> =
        { _, _, _ -> CompletableDeferred(Unit) }

    var onRespondToServerRequest: Boolean = true

    var onRespondToClarify: Boolean = true

    var onConnectionBinding: (String) -> ConnectionBinding? = { binding }

    data class SendCall(val method: String, val params: Map<String, Any>)

    data class SentMessage(val sessionId: String, val text: String, val queued: Boolean)

    data class RequestCall(val method: String, val params: Map<String, Any>, val timeoutMs: Long)

    fun nextSendId(): String = "req-id-${++sendCount}"

    fun nextMessageId(): String = "req-msg-${++messageCount}"

    override fun connect() {
        status.value = ConnectionStatus.CONNECTING
    }

    override fun disconnect(clearPendingMessages: Boolean) {
        status.value = ConnectionStatus.DISCONNECTED
    }

    override fun send(
        method: String,
        params: Map<String, Any>,
        onSent: ((String) -> Unit)?,
    ): String {
        sends += SendCall(method, params)
        val id = onSend(method, params)
        onSent?.invoke(id)
        return id
    }

    override fun request(
        method: String,
        params: Map<String, Any>,
        timeoutMs: Long,
    ): CompletableDeferred<Any?> {
        requests += RequestCall(method, params, timeoutMs)
        return onRequest(method, params, timeoutMs)
    }

    override fun requestForConnection(
        binding: ConnectionBinding,
        method: String,
        params: Map<String, Any>,
        timeoutMs: Long,
    ): CompletableDeferred<Any?> {
        requests += RequestCall(method, params, timeoutMs)
        return onRequest(method, params, timeoutMs)
    }

    override fun privilegedRequest(
        method: String,
        binding: PrivilegedRequestBinding,
        params: Map<String, String>,
    ): CompletableDeferred<Any?> = onPrivilegedRequest(method, binding, params)

    override fun respondToServerRequest(
        binding: ServerRequestBinding,
        result: JsonElement,
    ): Boolean = onRespondToServerRequest

    override fun respondToClarify(
        sessionId: String,
        clarifyRequestId: String,
        questionId: String?,
        answer: String,
        sourceProfileId: String,
        sourceConnectionGeneration: Int,
    ): Boolean = onRespondToClarify

    override fun sendMessage(
        sessionId: String,
        text: String,
        onSent: ((String) -> Unit)?,
        queued: Boolean,
    ): String {
        sentMessages += SentMessage(sessionId, text, queued)
        val id = onSendMessage(sessionId, text, queued)
        onSent?.invoke(id)
        return id
    }

    override fun sendMessageForConnection(
        binding: ConnectionBinding,
        sessionId: String,
        text: String,
        onSent: ((String) -> Unit)?,
    ): Boolean {
        sentMessages += SentMessage(sessionId, text, queued = false)
        onSent?.invoke(nextMessageId())
        return true
    }

    override fun sendRedirectForConnection(
        binding: ConnectionBinding,
        sessionId: String,
        text: String,
        onSent: ((String) -> Unit)?,
    ): Boolean {
        redirects += SentMessage(sessionId, text, queued = false)
        onSent?.invoke(nextMessageId())
        return true
    }

    override fun connectionBinding(expectedProfileId: String): ConnectionBinding? =
        onConnectionBinding(expectedProfileId)

    override fun isConnectionBindingCurrent(binding: ConnectionBinding): Boolean = bindingIsCurrent

    override fun rejectAllPending(error: HermesWsClient.HermesRpcException) = Unit
}

/**
 * A `ChatGateway` a test can drive. `hermesApi` defaults to a relaxed mock so callers that do not care
 * about HTTP get a working object.
 */
class FakeChatGateway(
    override var hermesApi: HermesApi = mockk(relaxed = true),
) : ChatGateway {
    override var selectedProfileId: String? = "profile-test"

    var clearedProfile = false

    var onLogin: suspend (jsonBody: String) -> Int = { 200 }

    override suspend fun currentProfileName(): String? = selectedProfileId

    override suspend fun loginWithPassword(
        jsonBody: String,
        timeoutMillis: Long,
    ): Int = onLogin(jsonBody)

    override fun setSelectedProfile(profile: String?) {
        selectedProfileId = profile
    }

    override fun clearSelectedProfile() {
        clearedProfile = true
        selectedProfileId = null
    }
}
