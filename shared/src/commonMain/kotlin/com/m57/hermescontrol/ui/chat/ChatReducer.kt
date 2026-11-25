package com.m57.hermescontrol.ui.chat

/**
 * The one place a new chat state is produced.
 *
 * Pure: same state and same intent always give the same result. No network, no clock, no reading a field from
 * anywhere else. That is what makes it testable and what makes an update atomic — a caller sends one intent and
 * gets one whole new state, so the screen cannot show a half-applied change.
 *
 * This is a composing pass, not a rule book. Each slice answers for itself and ignores what is not its business,
 * so one intent can reach several slices in the same pass and still produce exactly one new state. The rules
 * live in [ConnectionReducer], [ComposerReducer] and [TranscriptReducer].
 */
object ChatReducer {
    fun reduce(
        state: ChatUiState,
        intent: ChatIntent,
    ): ChatUiState =
        state.copy(
            transcript = reduceTranscript(state.transcript, intent),
            composer = ComposerReducer.reduce(state.composer, intent),
            connection = ConnectionReducer.reduce(state.connection, intent),
            overlays = reduceOverlays(state.overlays, intent),
        )

    /**
     * The adapter onto [TranscriptReducer], which predates [ChatIntent] and takes its own intent type.
     *
     * Not a layer with no job: it unwraps. The transcript slice already worked by intent before the rest of the
     * screen did, and rewriting it to take [ChatIntent] would churn a reducer that is already tested.
     */
    private fun reduceTranscript(
        state: TranscriptState,
        intent: ChatIntent,
    ): TranscriptState =
        when (intent) {
            is ChatIntent.Transcript -> TranscriptReducer.reduce(state, intent.intent)

            // The account changed, so the whole transcript is the old account's.
            ChatIntent.ProfileChanged -> TranscriptReducer.reduce(state, TranscriptIntent.ProfileSwitched)

            // The model changed, so its measured context window is no longer known.
            is ChatIntent.SessionModelConfirmed ->
                TranscriptReducer.reduce(state, TranscriptIntent.SessionModelConfirmed)

            // Not this slice's business.
            else -> state
        }

    /** The adapter onto [OverlaysReducer]. See [reduceTranscript] for why an adapter and not a direct call. */
    private fun reduceOverlays(
        state: OverlaysState,
        intent: ChatIntent,
    ): OverlaysState =
        when (intent) {
            is ChatIntent.SessionModelConfirmed ->
                OverlaysReducer.reduce(state, OverlaysIntent.ModelSwitchConfirmationDismissed)

            // Not this slice's business.
            else -> state
        }
}
