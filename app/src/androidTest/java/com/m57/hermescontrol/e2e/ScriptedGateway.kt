package com.m57.hermescontrol.e2e

import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.channels.Channel

/**
 * Scripted JSON-RPC WebSocket gateway — the single source of truth for the
 * gateway's RPC choreography, shared by Robolectric (JVM) and Espresso
 * (emulator) tests. Each [step] is one scripted behavior; [ClientCall]
 * captures what the app actually sent, for address-by assertions.
 *
 * Encodes the real gateway behaviors that produced field bugs:
 *  - RESUME_FAST_PATH: live session → payload with session_key immediately
 *  - RESUME_REAPED: session was reaped → resume re-registers → session_key
 *  - TITLE_REJECT_STORAGE_ID: bare session.title with a storage id → 4001
 *  - TITLE_ACCEPT_RUNTIME: runtime-id title → ok
 *
 * Usage (Robolectric):
 *   val gw = ScriptedGateway()
 *   gw.expect(RESUME_FAST_PATH("stored-1"))
 *   gw.expect(TITLE_ACCEPT_RUNTIME(...))
 *   HermesWsClient.connect(gw.url)  // test wiring
 *   ... app actions ...
 *   gw.assertAllConsumed()
 *
 * Usage (Espresso): same, served over MockWebServer's WebSocket upgrade.
 */
class ScriptedGateway {

    data class ClientCall(
        val method: String,
        val params: Map<String, Any?>,
        val id: String,
    )

    sealed interface Step {
        /** Reply to the next client call with this result JSON. */
        data class Reply(val json: String) : Step

        /** After replying, push an async server event (session.info etc.). */
        data class Push(val json: String) : Step

        /** Fail the next call with a JSON-RPC error envelope. */
        data class Fail(val code: Int, val message: String) : Step

        /** Assert the next client call matches method + params subset. */
        data class Expect(
            val method: String,
            val paramsSubset: Map<String, Any?> = emptyMap(),
        ) : Step
    }

    private val steps = ConcurrentLinkedQueue<Step>()
    val received = ConcurrentLinkedQueue<ClientCall>()

    /** Pushed (unsolicited) server→client events, drained by the transport. */
    val pushChannel = Channel<String>(capacity = 64)

    fun enqueue(vararg steps: Step) {
        steps.forEach { this.steps.add(it) }
    }

    /** Transport adapter calls this on every client frame. Returns the reply frame, if scripted. */
    fun onClientFrame(frame: String): String? {
        val call = parseCall(frame) ?: return null
        received.add(call)
        var reply: String? = null
        while (true) {
            val step = steps.poll() ?: break
            when (step) {
                is Step.Expect -> {
                    require(step.method == call.method) {
                        "Script mismatch: expected ${step.method}, got ${call.method}"
                    }
                    step.paramsSubset.forEach { (k, v) ->
                        require(call.params[k] == v) {
                            "Script mismatch on ${k}: expected ${v}, got ${call.params[k]}"
                        }
                    }
                }
                is Step.Reply -> { reply = step.json; break }
                is Step.Fail -> {
                    reply = errorEnvelope(call.id, step.code, step.message); break
                }
                is Step.Push -> {
                    // Unsolicited: deliver out-of-band, keep scripting.
                    pushChannel.trySend(step.json)
                }
            }
        }
        return reply
    }

    /** Drain any queued async pushes (transport calls this after a reply). */
    suspend fun drainPushes(onFrame: (String) -> Unit) {
        for (f in pushChannel) onFrame(f)
    }

    fun assertAllConsumed() {
        val remaining = steps.toList()
        check(remaining.isEmpty()) { "Unconsumed scripted steps: ${remaining}" }
    }

    fun assertSent(method: String, paramsSubset: Map<String, Any?>) {
        val match = received.any { call ->
            call.method == method && paramsSubset.all { (k, v) -> call.params[k] == v }
        }
        check(match) {
            "No ${method} call with ${paramsSubset}; got: ${received.toList()}"
        }
    }

    fun assertNeverSent(method: String, paramsSubset: Map<String, Any?> = emptyMap()) {
        val bad = received.any { call ->
            call.method == method && paramsSubset.all { (k, v) -> call.params[k] == v }
        }
        check(!bad) { "${method} WAS sent with ${paramsSubset}: ${received.toList()}" }
    }

    private fun parseCall(frame: String): ClientCall? = runCatching {
        val o = org.json.JSONObject(frame)
        ClientCall(
            method = o.getString("method"),
            params = o.getJSONObject("params").let { jo ->
                buildMap { for (k in jo.keys()) put(k, jo.opt(k)) }
            },
            id = o.optString("id", ""),
        )
    }.getOrNull()

    companion object {
        fun errorEnvelope(id: String, code: Int, message: String) =
            """{"jsonrpc":"2.0","id":"${id}","error":{"code":${code},"message":"${message}","data":null}}"""

        fun resultEnvelope(id: String, resultJson: String) =
            """{"jsonrpc":"2.0","id":"${id}","result":${resultJson}}"""

        // ── Choreography builders (the field-bug sequences) ─────────────────

        /** Resume fast-path ack for a live session. */
        fun resumeAck(id: String, storageId: String, runtimeId: String) = Step.Reply(
            resultEnvelope(
                id,
                """{"session_id":"${runtimeId}","resumed":"${storageId}",""" +
                    """"message_count":0,"messages":[],"session_key":"${storageId}",""" +
                    """"status":"idle","running":false}""",
            ),
        )

        /** Bare session.title with a storage id → the gateway 4001s (reaped). */
        fun titleRejectStorageId(id: String, storageId: String) = Step.Fail(
            code = 4001,
            message = "session not found",
        ).let { it } // envelope built by Fail handling; id carried via call

        /** session.title by runtime/session_key → accepted. */
        fun titleAccept(id: String, title: String) = Step.Reply(
            resultEnvelope(id, """{"pending":false,"title":"${title}"}"""),
        )
    }
}
