package com.m57.hermescontrol.ui.chat

/**
 * Overlay transitions as data — clarify first, because it is the only overlay with an identity.
 *
 * Every clarify write in the codebase is guarded: "clear it only if it is still the one I am looking
 * at". Those guards were written three times in two files with slightly different comparisons
 * (`== expected`, id + session + profile + generation). Expressing the guard inside the reducer means
 * there is one comparison to read and one to test, and a mismatched guard returns the state untouched
 * instead of an accidental clear.
 */
sealed interface OverlaysIntent {
    /**
     * Replace the clarify request **only if** it is still [expected]. A null [replacement] closes it.
     *
     * The identity check is deliberate: answers arrive asynchronously, so a response for a request the
     * user has already moved past must not overwrite the newer one.
     */
    data class ClarifyReplaced(
        val expected: ClarifyUi,
        val replacement: ClarifyUi?,
    ) : OverlaysIntent

    /**
     * The gateway says this clarify expired. Matched on id **and** source (session, profile,
     * connection generation) so a late expiry from an old connection cannot close a live prompt.
     */
    data class ClarifyExpired(
        val clarifyId: String?,
        val sessionId: String?,
        val sourceProfileId: String?,
        val connectionGeneration: Int?,
    ) : OverlaysIntent

    /**
     * The gateway cancelled this server request. Matched on the request binding, not the clarify id:
     * a cancellation names the request, and the prompt may carry a different id.
     */
    data class ClarifyCancelled(
        val requestId: String?,
        val sessionId: String?,
        val sourceProfileId: String?,
        val connectionGeneration: Int?,
    ) : OverlaysIntent

    /** The prompt is done — the turn answered it, or it was dismissed. Unconditional by design. */
    data object ClarifyCleared : OverlaysIntent
}

object OverlaysReducer {
    fun reduce(
        state: OverlaysState,
        intent: OverlaysIntent,
    ): OverlaysState =
        when (intent) {
            is OverlaysIntent.ClarifyReplaced ->
                if (state.clarifyRequest != intent.expected) {
                    state
                } else {
                    state.copy(clarifyRequest = intent.replacement)
                }

            is OverlaysIntent.ClarifyExpired -> {
                val current = state.clarifyRequest
                val matches =
                    current != null &&
                        current.clarifyId == intent.clarifyId &&
                        current.sessionId == intent.sessionId &&
                        current.sourceProfileId == intent.sourceProfileId &&
                        current.connectionGeneration == intent.connectionGeneration
                if (matches) state.copy(clarifyRequest = null) else state
            }

            is OverlaysIntent.ClarifyCancelled -> {
                val binding = state.clarifyRequest?.serverRequestBinding
                val matches =
                    binding != null &&
                        binding.requestId == intent.requestId &&
                        binding.runtimeSessionId == intent.sessionId &&
                        binding.profileId == intent.sourceProfileId &&
                        binding.connectionGeneration == intent.connectionGeneration
                if (matches) state.copy(clarifyRequest = null) else state
            }

            OverlaysIntent.ClarifyCleared -> state.copy(clarifyRequest = null)
        }
}
