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
 * Step matching rules (issue #e2e-23):
 *
 *  - [Step.Expect] always runs at the queue head and asserts the next call
 *    matches its method + params subset.
 *
 *  - [Step.Reply] / [Step.Fail] are legacy FIFO. They carry a full JSON-RPC
 *    envelope with the id the test baked in. They only fire when the next
 *    call matches both `method` (if non-null) and `idHint` (if non-null).
 *
 *  - [Step.ReplyFor(method, resultJson)] / [Step.FailFor(method, ...)]
 *    are method-aware. On dispatch, the gateway finds the first queued
 *    method-aware step whose `method` equals the call's method, removes it,
 *    and wraps `resultJson` in an envelope using the *actual* call id.
 *    This handles the concurrent send patterns in
 *    [com.m57.hermescontrol.ui.chat.ChatViewModel.handleGatewayReady]
 *    (loadSessions + fetchCommandCatalog + optional session.resume fire on
 *    separate coroutines, so request id order is nondeterministic).
 *
 *  - [Step.Push] is out-of-band and is drained through [pushChannel].
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
        /** Reply to the next client call with this full envelope JSON. Legacy FIFO. */
        data class Reply(
            val json: String,
            /** When non-null, only fires for a call whose method matches. */
            val method: String? = null,
            /** When non-null, only fires for a call whose id matches. */
            val idHint: String? = null,
        ) : Step

        /** Reply to the next call whose method equals [method]; envelope is
         *  built using the actual call id at dispatch time. */
        data class ReplyFor(
            val method: String,
            val resultJson: String,
        ) : Step

        /** After replying, push an async server event (session.info etc.). */
        data class Push(val json: String) : Step

        /** Fail the next client call with a JSON-RPC error envelope. Legacy FIFO. */
        data class Fail(
            val code: Int,
            val message: String,
            /** When non-null, only fires for a call whose method matches. */
            val method: String? = null,
            /** When non-null, only fires for a call whose id matches. */
            val idHint: String? = null,
        ) : Step

        /** Fail the next call whose method equals [method]; envelope uses the
         *  actual call id at dispatch time. */
        data class FailFor(
            val method: String,
            val code: Int,
            val message: String,
        ) : Step

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
        return dispatchFor(call)
    }

    /**
     * Find the next queued step that fits this call and produce a reply.
     *
     * Two passes:
     *  1. Look for a method-aware ReplyFor / FailFor whose method equals
     *     `call.method`. If found:
     *       a. Drain any Pushes that were queued strictly before it into
     *          the push channel (out-of-band).
     *       b. Remove the matched step.
     *       c. Return the envelope built with the actual `call.id`.
     *  2. Otherwise walk the queue FIFO: Skip non-matching legacy Replies /
     *     Fails (they remain queued for a later matching call), consume
     *     Expect steps (asserting the call matches), and drain Pushes.
     */
    private fun dispatchFor(call: ClientCall): String? {
        // Pass 1: method-aware match.
        val matchingReply = steps.firstOrNull { step ->
            when (step) {
                is Step.ReplyFor -> step.method == call.method
                is Step.FailFor -> step.method == call.method
                else -> false
            }
        }
        if (matchingReply != null) {
            // Drain any pushes strictly before the matched step (out-of-band).
            var consumed = false
            while (!consumed) {
                val head = steps.peek() ?: break
                if (head === matchingReply) {
                    steps.poll() // remove the matched step
                    consumed = true
                } else {
                    when (val step = steps.poll()) {
                        is Step.Push -> pushChannel.trySend(step.json)
                        else -> steps.add(step) // non-push legacy steps back at tail
                    }
                }
            }
            return when (matchingReply) {
                is Step.ReplyFor -> resultEnvelope(call.id, matchingReply.resultJson)
                is Step.FailFor -> errorEnvelope(
                    call.id,
                    matchingReply.code,
                    matchingReply.message,
                )
                else -> null
            }
        }

        // Pass 2: legacy FIFO.
        return legacyDispatch(call)
    }

    /**
     * Legacy FIFO walk. Each head step is classified:
     *  - Push: drained into pushChannel, removed.
     *  - Expect: always consumed (asserts the call).
     *  - Reply/Fail: consumed only if its method + idHint match the call;
     *    otherwise left in place and the walk bails.
     */
    private fun legacyDispatch(call: ClientCall): String? {
        var reply: String? = null
        while (true) {
            val step = steps.peek() ?: break
            when (step) {
                is Step.Expect -> {
                    steps.poll()
                    require(step.method == call.method) {
                        "Script mismatch: expected ${step.method}, got ${call.method}"
                    }
                    step.paramsSubset.forEach { (k, v) ->
                        require(call.params[k] == v) {
                            "Script mismatch on ${k}: expected ${v}, got ${call.params[k]}"
                        }
                    }
                    // Continue walking — Expect doesn't terminate.
                }
                is Step.Push -> {
                    steps.poll()
                    pushChannel.trySend(step.json)
                }
                is Step.Reply -> {
                    if (matchesMethodAndId(step.method, step.idHint, call)) {
                        steps.poll()
                        reply = step.json
                        break
                    } else {
                        // Leave for a later call.
                        break
                    }
                }
                is Step.Fail -> {
                    if (matchesMethodAndId(step.method, step.idHint, call)) {
                        steps.poll()
                        reply = errorEnvelope(call.id, step.code, step.message)
                        break
                    } else {
                        break
                    }
                }
                is Step.ReplyFor, is Step.FailFor -> {
                    // Method-aware steps not reached via pass 1 are unreachable
                    // from pass 2 in normal use; if somehow one is at the head
                    // and doesn't match (shouldn't happen), bail.
                    break
                }
            }
        }
        return reply
    }

    private fun matchesMethodAndId(
        stepMethod: String?,
        stepIdHint: String?,
        call: ClientCall,
    ): Boolean {
        if (stepMethod != null && stepMethod != call.method) return false
        if (stepIdHint != null && stepIdHint != call.id) return false
        return true
    }

    /** Diagnostic snapshot of the queue state — caller-visible in error messages. */
    fun dumpState(): String =
        "steps=${steps.toList()} received=${received.toList()}"

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
            """{"jsonrpc":"2.0","id":"${id}","error":{"code":${code},"message":"${message}"}}"""

        fun resultEnvelope(id: String, resultJson: String) =
            """{"jsonrpc":"2.0","id":"${id}","result":${resultJson}}"""

        // ── Choreography builders (the field-bug sequences) ─────────────────

        /**
         * Resume fast-path ack — keys match the real gateway's
         * _live_session_payload (info/message_count/messages/running/
         * session_id/session_key/started_at/status). The real gateway has
         * NO "resumed" key; the app falls back to the requested id, so
         * fidelity wins over the convenience key. Method-aware.
         */
        fun resumeAck(storageId: String, runtimeId: String) = Step.ReplyFor(
            method = "session.resume",
            resultJson = """{"info":{"cwd":"/tmp","lazy":true,"skills":{},"tools":{}},""" +
                """"message_count":0,"messages":[],"running":false,""" +
                """"session_id":"$runtimeId","session_key":"$storageId",""" +
                """"started_at":0.0,"status":"idle"}""",
        )

        /** Legacy FIFO variant for tests that pin a specific id. */
        @Suppress("unused")
        fun resumeAck(id: String, storageId: String, runtimeId: String) = Step.Reply(
            resultEnvelope(
                id,
                """{"info":{"cwd":"/tmp","lazy":true,"skills":{},"tools":{}},""" +
                    """"message_count":0,"messages":[],"running":false,""" +
                    """"session_id":"$runtimeId","session_key":"$storageId",""" +
                    """"started_at":0.0,"status":"idle"}""",
            ),
        )

        /** Bare session.title with a storage id → the gateway 4001s (reaped). */
        fun titleRejectStorageId() = Step.FailFor(
            method = "session.title",
            code = 4001,
            message = "session not found",
        )

        /** session.title by runtime/session_key → accepted. Method-aware. */
        fun titleAccept(title: String) = Step.ReplyFor(
            method = "session.title",
            resultJson = """{"pending":false,"title":"${title}"}""",
        )

        /** Legacy FIFO variant. */
        @Suppress("unused")
        fun titleAccept(id: String, title: String) = Step.Reply(
            resultEnvelope(id, """{"pending":false,"title":"${title}"}"""),
        )

        // ── Convenience builders for gateway.ready-time RPCs ──────────────

        /** session.list response with the given sessions JSON array body. */
        fun sessionList(sessionsJson: String) = Step.ReplyFor(
            method = "session.list",
            resultJson = """{"sessions":${sessionsJson}}""",
        )

        /** commands.catalog no-op ack (empty result body). */
        fun commandsCatalogEmpty() = Step.ReplyFor(
            method = "commands.catalog",
            resultJson = "{}",
        )
    }
}
