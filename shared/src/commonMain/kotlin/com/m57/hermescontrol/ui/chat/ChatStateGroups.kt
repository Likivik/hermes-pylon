package com.m57.hermescontrol.ui.chat

import com.m57.hermescontrol.data.model.Attachment
import com.m57.hermescontrol.data.model.ModelProvider
import com.m57.hermescontrol.data.model.PinnedModel
import com.m57.hermescontrol.data.ws.CommandCatalog
import com.m57.hermescontrol.data.ws.ConnectionStatus
import com.m57.hermescontrol.ui.chat.rail.RailLoadStatus
import com.m57.hermescontrol.ui.common.SecureGatewayMediaRequest

// The chat screen's state, split by *who owns the lifecycle*.
//
// One 56-field class made illegal combinations representable and cheap to write by accident: the
// loading-overlay bug came from `isLoading` meaning "a history fetch is in flight" while the screen
// read it as "not connected". Each group below answers to one subsystem, and a change in one cannot
// silently invalidate another.
//
// These types are not a public API of the app — [ChatUiState] still exposes every field under its old
// name, so screens and tests read the same values as before.

/** Conversation content and its paging/streaming lifecycle. */
data class TranscriptState(
    val messages: List<ChatMessage> = emptyList(),
    val currentSessionId: String? = null,
    val isSessionReady: Boolean = false,
    val chatTitle: String = "Hermes",
    val isAgentTyping: Boolean = false,
    val isThinking: Boolean = false,
    val thinkingText: String = "",
    val isLoading: Boolean = false,
    val isLoadingOlder: Boolean = false,
    val hasOlderMessages: Boolean = false,
    val streamingMessage: ChatMessage? = null,
    val errorMessage: String? = null,
    val staleSince: Long? = null,
    val reactionKind: String? = null,
    val reactionTriggerId: Long = 0L,
    val subagentIndicators: List<SubagentIndicator> = emptyList(),
    val contextUsage: ContextUsage? = null,
    val todos: List<TodoItem> = emptyList(),
)

/** The session rail: its own fetch lifecycle, its own failure mode, its own picker. */
data class RailState(
    val sessions: List<SessionUi> = emptyList(),
    val railRefreshing: Boolean = false,
    val railStatus: RailLoadStatus = RailLoadStatus.Loading,
    val showSessionPicker: Boolean = false,
)

/** What the user is composing: draft, attachments, commands, model choice, typing prefs. */
data class ComposerState(
    val pendingAttachments: List<Attachment> = emptyList(),
    val pendingPrefillText: String? = null,
    val commandCatalog: CommandCatalog = CommandCatalog(),
    val slashUsageCounts: Map<String, Int> = emptyMap(),
    val openHistoryRequested: Boolean = false,
    val reasoningLevel: String? = null,
    val currentSessionModel: String? = null,
    val terminalBackend: String? = null,
    val modelContextLength: Long? = null,
    val modelContextLengthModel: String? = null,
    val typingEffectEnabled: Boolean = true,
    val typingEffectDelayMs: Int = 30,
)

/** Transport health. Deliberately three fields: status, the pill text, and the first-connect latch. */
data class ConnectionState(
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val statusPill: String? = null,
    val hasConnectedOnce: Boolean = false,
)

/** Everything modal or transient that sits on top of the conversation. */
data class OverlaysState(
    val backgroundCompleteMessage: String? = null,
    val openError: String? = null,
    val openingAttachmentPath: String? = null,
    val mediaPlayerRequest: SecureGatewayMediaRequest? = null,
    val clarifyRequest: ClarifyUi? = null,
    val sudoPrompt: SudoPromptUi? = null,
    val secretPrompt: SecretPromptUi? = null,
    val vaultPrompt: VaultPromptUi? = null,
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val searchMatchIndices: List<Int> = emptyList(),
    val currentSearchMatchIndex: Int = -1,
    val showModelPicker: Boolean = false,
    val modelPickerProviders: List<ModelProvider> = emptyList(),
    val modelPickerPinned: List<PinnedModel> = emptyList(),
    val modelPickerLoading: Boolean = false,
    val modelInventoryResolved: Boolean = false,
    val modelSwitchConfirmation: ModelSwitchConfirmation? = null,
    val showContextDetail: Boolean = false,
)
