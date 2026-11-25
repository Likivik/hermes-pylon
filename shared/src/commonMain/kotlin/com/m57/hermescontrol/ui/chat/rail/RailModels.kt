package com.m57.hermescontrol.ui.chat.rail

import com.m57.hermescontrol.ui.chat.RailMeta
import com.m57.hermescontrol.ui.chat.SessionUi

/**
 * Whether the rail has rows, and when it does not, **why**.
 *
 * A rail with zero rows used to render nothing at all, so "no sessions" and
 * "the request failed" looked identical — the rail simply vanished. Every
 * caller now gets the reason, and the rail renders it.
 */
sealed interface RailLoadStatus {
    /** First load, nothing decided yet. */
    data object Loading : RailLoadStatus

    /** Loaded successfully and there is at least one row. */
    data object Ready : RailLoadStatus

    /** Loaded successfully; the server genuinely returned no sessions. */
    data object Empty : RailLoadStatus

    /**
     * The last attempt failed. [reason] is short and user-facing ("offline",
     * "HTTP 401"); [needsSignIn] asks for a sign-in affordance instead of a
     * plain retry.
     */
    data class Unavailable(
        val reason: String,
        val needsSignIn: Boolean = false,
    ) : RailLoadStatus
}

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
    /** Reorder mode: long-press grabs & drags instead of opening the menu. */
    val reordering: Boolean = false,
    /** Rows plus the reason the rail is empty — see [RailLoadStatus]. */
    val status: RailLoadStatus = RailLoadStatus.Loading,
)

/** Sealed rail events — one callback, scales without signature churn. */
sealed interface RailEvent {
    data class Switch(val sessionId: String) : RailEvent

    data class TogglePin(val sessionId: String) : RailEvent

    data class Delete(val sessionId: String) : RailEvent

    data class Edit(val sessionId: String) : RailEvent

    /** Emitted on drop with the full desired order (pinned + fresh + stale). */
    data class Reorder(val order: List<String>) : RailEvent

    /** Enter/exit reorder mode (long-press-drag grab instead of the menu). */
    data object ToggleReorder : RailEvent

    data class ArchiveToggle(val open: Boolean) : RailEvent

    /** The rail is empty because the last refresh failed — retry it. */
    data object Retry : RailEvent

    /** The rail is empty because the session expired — go to sign-in. */
    data object SignIn : RailEvent
}
