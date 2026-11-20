package com.m57.hermescontrol.ui.chat

import com.m57.hermescontrol.data.model.Attachment
import com.m57.hermescontrol.data.model.ModelProvider
import com.m57.hermescontrol.data.model.PinnedModel
import com.m57.hermescontrol.data.ws.CommandCatalog
import com.m57.hermescontrol.data.ws.ConnectionStatus
import com.m57.hermescontrol.data.ws.PrivilegedRequestBinding
import com.m57.hermescontrol.data.ws.ServerRequestBinding
import com.m57.hermescontrol.ui.chat.rail.RailLoadStatus
import com.m57.hermescontrol.ui.common.SecureGatewayMediaRequest
import com.m57.hermescontrol.util.nowMillis

//
// Extracted from ChatViewModel.kt (which stays in :app — it is an
// AndroidViewModel and owns the live connection/session machinery). These are
// plain data classes, so the console client can render the same prompt and
// session state without the Android ViewModel.

/**
 * The chat screen's whole state, composed of five lifecycle-owned groups.
 *
 * **Reads are unchanged.** Every field below is a getter over its group, so `state.messages`,
 * `state.isConnected` and the rest still work at every call site, including tests.
 *
 * **Writes moved.** A getter cannot participate in a data-class `copy`, so a mutation now says which
 * group it belongs to: `copy(transcript = transcript.copy(
 transcript = transcript.transcript.copy(messages = …),
 ))`. That is the point of the
 * split — the compiler asks the author to name the owner of the change.
 */
data class ChatUiState(
    val transcript: TranscriptState = TranscriptState(),
    val rail: RailState = RailState(),
    val composer: ComposerState = ComposerState(),
    val connection: ConnectionState = ConnectionState(),
    val overlays: OverlaysState = OverlaysState(),
) {
    // Transcript
    val messages: List<ChatMessage> get() = transcript.messages
    val currentSessionId: String? get() = transcript.currentSessionId
    val isSessionReady: Boolean get() = transcript.isSessionReady
    val chatTitle: String get() = transcript.chatTitle
    val isAgentTyping: Boolean get() = transcript.isAgentTyping
    val isThinking: Boolean get() = transcript.isThinking
    val thinkingText: String get() = transcript.thinkingText
    val isLoading: Boolean get() = transcript.isLoading
    val isLoadingOlder: Boolean get() = transcript.isLoadingOlder
    val hasOlderMessages: Boolean get() = transcript.hasOlderMessages
    val streamingMessage: ChatMessage? get() = transcript.streamingMessage
    val errorMessage: String? get() = transcript.errorMessage
    val staleSince: Long? get() = transcript.staleSince
    val reactionKind: String? get() = transcript.reactionKind
    val reactionTriggerId: Long get() = transcript.reactionTriggerId
    val subagentIndicators: List<SubagentIndicator> get() = transcript.subagentIndicators
    val contextUsage: ContextUsage? get() = transcript.contextUsage
    val todos: List<TodoItem> get() = transcript.todos

    // Rail
    val sessions: List<SessionUi> get() = rail.sessions
    val railRefreshing: Boolean get() = rail.railRefreshing
    val railStatus: RailLoadStatus get() = rail.railStatus
    val showSessionPicker: Boolean get() = rail.showSessionPicker

    // Composer
    val pendingAttachments: List<Attachment> get() = composer.pendingAttachments
    val pendingPrefillText: String? get() = composer.pendingPrefillText
    val commandCatalog: CommandCatalog get() = composer.commandCatalog
    val slashUsageCounts: Map<String, Int> get() = composer.slashUsageCounts
    val openHistoryRequested: Boolean get() = composer.openHistoryRequested
    val reasoningLevel: String? get() = composer.reasoningLevel
    val currentSessionModel: String? get() = composer.currentSessionModel
    val terminalBackend: String? get() = composer.terminalBackend
    val modelContextLength: Long? get() = composer.modelContextLength
    val modelContextLengthModel: String? get() = composer.modelContextLengthModel
    val typingEffectEnabled: Boolean get() = composer.typingEffectEnabled
    val typingEffectDelayMs: Int get() = composer.typingEffectDelayMs

    // Connection
    val connectionStatus: ConnectionStatus get() = connection.connectionStatus
    val statusPill: String? get() = connection.statusPill
    val hasConnectedOnce: Boolean get() = connection.hasConnectedOnce

    // Overlays
    val backgroundCompleteMessage: String? get() = overlays.backgroundCompleteMessage
    val openError: String? get() = overlays.openError
    val openingAttachmentPath: String? get() = overlays.openingAttachmentPath
    val mediaPlayerRequest: SecureGatewayMediaRequest? get() = overlays.mediaPlayerRequest
    val clarifyRequest: ClarifyUi? get() = overlays.clarifyRequest
    val sudoPrompt: SudoPromptUi? get() = overlays.sudoPrompt
    val secretPrompt: SecretPromptUi? get() = overlays.secretPrompt
    val vaultPrompt: VaultPromptUi? get() = overlays.vaultPrompt
    val isSearchActive: Boolean get() = overlays.isSearchActive
    val searchQuery: String get() = overlays.searchQuery
    val searchMatchIndices: List<Int> get() = overlays.searchMatchIndices
    val currentSearchMatchIndex: Int get() = overlays.currentSearchMatchIndex
    val showModelPicker: Boolean get() = overlays.showModelPicker
    val modelPickerProviders: List<ModelProvider> get() = overlays.modelPickerProviders
    val modelPickerPinned: List<PinnedModel> get() = overlays.modelPickerPinned
    val modelPickerLoading: Boolean get() = overlays.modelPickerLoading
    val modelInventoryResolved: Boolean get() = overlays.modelInventoryResolved
    val modelSwitchConfirmation: ModelSwitchConfirmation? get() = overlays.modelSwitchConfirmation
    val showContextDetail: Boolean get() = overlays.showContextDetail

    /** Convenience — derived from [connectionStatus]. */
    val isConnected: Boolean get() = connection.connectionStatus == ConnectionStatus.CONNECTED
}

data class SessionUi(
    val id: String,
    val title: String,
    val messageCount: Int = 0,
    val parentSessionId: String? = null,
    val depth: Int = 0,
    // Unix seconds of last activity (from session.list "started_at"); 0 = unknown
    val lastActive: Long = 0L,
    // Gateway `source` ("cron", "desktop", …) when the payload carries it; null otherwise.
    val source: String? = null,
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
