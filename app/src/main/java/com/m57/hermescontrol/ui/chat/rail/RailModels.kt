package com.m57.hermescontrol.ui.chat.rail

import com.m57.hermescontrol.ui.chat.RailMeta
import com.m57.hermescontrol.ui.chat.SessionUi

/**
 * Single immutable snapshot of everything the rail renders. The ViewModel
 * exposes one StateFlow of this; the rail stays stateless and previewable.
 */
data class RailUiModel(
    val sessions: List<SessionUi> = emptyList(),
    val currentSessionId: String? = null,
    val pinnedSessionIds: Set<String> = emptySet(),
    val railMeta: Map<String, RailMeta> = emptyMap(),
    /** Manual order (session ids), pinned + fresh + stale as last arranged. */
    val order: List<String> = emptyList(),
    /** Whether the archive (stale drawer) is open — owned by the VM so it survives process death. */
    val archiveOpen: Boolean = false,
)

/** Sealed rail events — one callback, scales without signature churn. */
sealed interface RailEvent {
    data class Switch(val sessionId: String) : RailEvent
    data class TogglePin(val sessionId: String) : RailEvent
    data class Delete(val sessionId: String) : RailEvent
    data class Edit(val sessionId: String) : RailEvent
    /** Emitted on drop with the full desired order (pinned + fresh + stale). */
    data class Reorder(val order: List<String>) : RailEvent
    data class ArchiveToggle(val open: Boolean) : RailEvent
}
