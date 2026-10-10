package com.m57.hermescontrol.ui.chat

import com.m57.hermescontrol.data.model.Attachment
import com.m57.hermescontrol.data.model.ModelProvider
import com.m57.hermescontrol.data.model.PinnedModel
import com.m57.hermescontrol.data.ws.CommandCatalog
import com.m57.hermescontrol.data.ws.ConnectionStatus
import com.m57.hermescontrol.data.ws.PrivilegedRequestBinding
import com.m57.hermescontrol.data.ws.ServerRequestBinding
import com.m57.hermescontrol.ui.common.SecureGatewayMediaRequest
import com.m57.hermescontrol.util.nowMillis

/** Chat UI state and prompt models, shared across platforms.
 *
 * Extracted from ChatViewModel.kt (which stays in :app — it is an
 * AndroidViewModel and owns the live connection/session machinery). These are
 * plain data classes, so the console client can render the same prompt and
 * session state without the Android ViewModel.
 */
data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val currentSessionId: String? = null,
    val isSessionReady: Boolean = false,
    val sessions: List<SessionUi> = emptyList(),
    val railRefreshing: Boolean = false,
    val chatTitle: String = "Hermes",
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val statusPill: String? = null,
    val isAgentTyping: Boolean = false,
    val isThinking: Boolean = false,
    val thinkingText: String = "",
    val isLoading: Boolean = false,
    val isLoadingOlder: Boolean = false,
    val hasOlderMessages: Boolean = false,
    /** Standalone streaming message — rendered after the main list. */
    val streamingMessage: ChatMessage? = null,
    val errorMessage: String? = null,
    // Background job completion toast (issue #527) — non-blocking snackbar
    val backgroundCompleteMessage: String? = null,
    // Attachment open failure — surfaced as a non-blocking snackbar (issue #724)
    val openError: String? = null,
    val openingAttachmentPath: String? = null,
    val mediaPlayerRequest: SecureGatewayMediaRequest? = null,
    val clarifyRequest: ClarifyUi? = null,
    // Sudo / secret prompts — surfaced as dialogs (issue #524)
    val sudoPrompt: SudoPromptUi? = null,
    val secretPrompt: SecretPromptUi? = null,
    val vaultPrompt: VaultPromptUi? = null,
    val showSessionPicker: Boolean = false,
    // Search state
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val searchMatchIndices: List<Int> = emptyList(),
    val currentSearchMatchIndex: Int = -1,
    // Cached settings
    val typingEffectEnabled: Boolean = true,
    val typingEffectDelayMs: Int = 30,
    // Commands catalog
    val commandCatalog: CommandCatalog = CommandCatalog(),
    val slashUsageCounts: Map<String, Int> = emptyMap(),
    val openHistoryRequested: Boolean = false,
    // In-session model picker (issue #589) — surfaced when the user types /model
    // or taps the composer model chip. Mirror of the global model screen's
    // picker, but the selection hot-swaps the CURRENT session via config.set.
    val showModelPicker: Boolean = false,
    val modelPickerProviders: List<ModelProvider> = emptyList(),
    val modelPickerPinned: List<PinnedModel> = emptyList(),
    val modelPickerLoading: Boolean = false,
    val modelInventoryResolved: Boolean = false,
    // Current session's active provider/model label, used by the composer
    // model chip and picker title.
    val currentSessionModel: String? = null,
    val modelSwitchConfirmation: ModelSwitchConfirmation? = null,
    // Reasoning effort level for the current session
    val reasoningLevel: String? = null,
    val terminalBackend: String? = null,
    // Attachment state
    val pendingAttachments: List<Attachment> = emptyList(),
    // Reaction animation — set when a reaction WS event arrives, auto-clears
    val reactionKind: String? = null,
    /** Monotonic trigger ID so consecutive same-kind reactions re-animate. */
    val reactionTriggerId: Long = 0L,
    /** Subagent delegation indicators (issue #538) — transient UI state. */
    val subagentIndicators: List<SubagentIndicator> = emptyList(),
    /**
     * Context-window meter state. Fed by the gateway `usage` block on
     * `session.info` / `message.complete`; null until the session reports one.
     */
    val contextUsage: ContextUsage? = null,
    /**
     * Denominator fallback from `GET /api/model/info`, used only while the
     * session has not yet reported a live `context_max` over the WebSocket.
     */
    val modelContextLength: Long? = null,
    /** Provider-qualified model identity associated with [modelContextLength]. */
    val modelContextLengthModel: String? = null,
    /** Whether the tappable context breakdown sheet is open. */
    val showContextDetail: Boolean = false,
    /** Agent todo / plan items (issue #736). */
    val todos: List<TodoItem> = emptyList(),
    /** One-shot composer restoration produced by a successful `/undo`. */
    val pendingPrefillText: String? = null,
) {
    /** Convenience — derived from [connectionStatus]. */
    val isConnected: Boolean get() = connectionStatus == ConnectionStatus.CONNECTED
}

data class SessionUi(
    val id: String,
    val title: String,
    val messageCount: Int = 0,
    val parentSessionId: String? = null,
    val depth: Int = 0,
    // Unix seconds of last activity (from session.list "started_at"); 0 = unknown
    val lastActive: Long = 0L,
) {
    /**
     * Stale = known activity and idle for 7+ days. lastActive == 0 means
     * UNKNOWN, not old — unknown-activity sessions must render as fresh
     * (this exact rule previously emptied the rail for all mock/pipeline
     * sessions; real server feeds can also emit 0).
     */
    val isStale: Boolean
        get() =
            lastActive > 0 &&
                lastActive * 1000 < nowMillis() - STALE_THRESHOLD_MS

    companion object {
        private const val STALE_THRESHOLD_MS = 7L * 24 * 60 * 60 * 1000

        /**
         * Likivik patch: sort the rail newest-first, with pinned sessions lifted
         * to the top. Stable for ids equal on every axis so the rail keeps a
         * deterministic order across updates.
         */
        fun orderForRail(
            sessions: List<SessionUi>,
            pinnedIds: Set<String>,
        ): List<SessionUi> {
            val pinned = sessions.filter { it.id in pinnedIds }
            val others = sessions.filter { it.id !in pinnedIds }
            val byFreshness =
                compareByDescending<SessionUi> { it.lastActive }
                    .thenByDescending { it.messageCount }
                    .thenBy { it.id }
            return (pinned.sortedWith(byFreshness) + others.sortedWith(byFreshness))
        }
    }
}

data class ClarifyUi(
    val text: String,
    val options: List<String>,
    val clarifyId: String? = null,
    val questionId: String? = null,
    val multiSelect: Boolean = false,
    val questions: List<ClarifyQuestionUi> = emptyList(),
    val sessionId: String? = null,
    val sourceProfileId: String? = null,
    val connectionGeneration: Int? = null,
    val serverRequestBinding: ServerRequestBinding? = null,
    val lockedAnswers: Map<String, String> = emptyMap(),
) {
    val resolvedQuestions: List<ClarifyQuestionUi>
        get() =
            questions.ifEmpty {
                listOf(
                    ClarifyQuestionUi(
                        qid = questionId ?: "q0",
                        question = text,
                        choices = options,
                        multiSelect = multiSelect,
                    ),
                )
            }
}

data class ClarifyQuestionUi(
    val qid: String,
    val question: String,
    val choices: List<String> = emptyList(),
    val multiSelect: Boolean = false,
)

/**
 * String sent to the agent when a clarify prompt is dismissed (the Dismiss
 * button). This is a *reject* — "I'm not answering this question" — NOT an
 * instruction to proceed. Deliberately NOT the CLI's interrupt sentinel
 * ("...Use your best judgement to proceed."): a mobile Dismiss is a
 * skip-the-question gesture, not an interrupt of the whole turn. The agent is
 * unblocked but told no answer was given, so it re-asks or backs off rather
 * than charging ahead.
 */
const val CLARIFY_DISMISS_RESPONSE = "The user cancelled — no answer provided."

/**
 * Transient — not persisted. Holds a pending sudo.password request.
 *
 * Carries only the routing binding, never the password: the entered value lives
 * in the dialog's own composition and in the single transient RPC call.
 */
data class SudoPromptUi(
    val binding: PrivilegedRequestBinding,
    val serverRequestBinding: ServerRequestBinding? = null,
    val isSubmitting: Boolean = false,
) {
    val requestId: String get() = binding.requestId
    val fullBinding: Any get() = serverRequestBinding ?: binding
}

/**
 * Transient — not persisted. Holds a pending secret (token/password) request.
 *
 * Like [SudoPromptUi], this holds no secret value — only the request's routing
 * binding and the gateway's own non-secret prompt labels.
 */
data class SecretPromptUi(
    val binding: PrivilegedRequestBinding,
    val envVar: String? = null,
    val prompt: String? = null,
    val serverRequestBinding: ServerRequestBinding? = null,
    val isSubmitting: Boolean = false,
) {
    val fullBinding: Any get() = serverRequestBinding ?: binding
    val requestId: String get() = binding.requestId
}

data class VaultPromptUi(
    val binding: ServerRequestBinding,
    val method: String,
    val title: String? = null,
    val prompt: String? = null,
    val identifier: String? = null,
    val requestedOrigin: String? = null,
    val isSubmitting: Boolean = false,
) {
    val hasValidRequestedOrigin: Boolean
        get() = method != "vault.save_login" || requestedOrigin.isValidWebOrigin()
}

/**
 * Is this string a bare, absolute http(s) ORIGIN — scheme + host + optional
 * port, and nothing else?
 *
 * Multiplatform rewrite of the previous `java.net.URI` check (`URI` is JVM-only,
 * so it could not compile in commonMain). The rules are deliberately reproduced
 * from the tested behaviour of the Java version:
 *
 *  - scheme is `http`/`https`, case-insensitively
 *  - a host is present
 *  - an empty port (`https://host:`) is invalid — Java leaves it in the raw
 *    authority, so it is rejected here by the trailing-colon rule
 *  - a specified port must be 1..65535, which rejects `:0`, `:65536` and `:-1`
 *  - no userinfo, path, query or fragment
 *
 * Unlike `URI`, this does not validate IPv6 literal syntax beyond the bracket
 * form; the value is only ever displayed and echoed back to the prompt.
 */
private fun String?.isValidWebOrigin(): Boolean {
    val value = this ?: return false
    if (value.isBlank() || value != value.trim()) return false
    if (value.any { it.isWhitespace() }) return false

    val separator = value.indexOf("://")
    if (separator <= 0) return false
    val scheme = value.substring(0, separator)
    if (!scheme.equals("http", ignoreCase = true) && !scheme.equals("https", ignoreCase = true)) return false

    val authority = value.substring(separator + 3)
    if (authority.isEmpty()) return false
    if (authority.endsWith(":")) return false
    if (authority.any { it == '/' || it == '?' || it == '#' || it == '@' }) return false

    val host: String
    val portText: String?
    if (authority.startsWith("[")) {
        val close = authority.indexOf(']')
        if (close < 0) return false
        host = authority.substring(1, close)
        val rest = authority.substring(close + 1)
        portText =
            when {
                rest.isEmpty() -> null
                rest.startsWith(":") -> rest.substring(1)
                else -> return false
            }
        val hasIllegalHostCharacter =
            host.any { !(it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == ':' || it == '.') }
        if (host.isEmpty() || hasIllegalHostCharacter) {
            return false
        }
    } else {
        val colon = authority.indexOf(':')
        if (colon < 0) {
            host = authority
            portText = null
        } else {
            host = authority.substring(0, colon)
            portText = authority.substring(colon + 1)
        }
        if (host.isEmpty() || host.any { !(it.isLetterOrDigit() || it == '.' || it == '-') }) return false
    }

    if (portText != null) {
        if (portText.isEmpty() || portText.any { !it.isDigit() }) return false
        val port = portText.toIntOrNull() ?: return false
        if (port !in 1..65535) return false
    }
    return true
}

/** Expensive-model confirmation returned by the gateway's config.set RPC. */
data class ModelSwitchConfirmation(
    val message: String,
    val spec: String,
    val displayLabel: String?,
    val sessionId: String,
)
