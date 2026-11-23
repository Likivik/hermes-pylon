package com.m57.hermescontrol.ui.chat

/**
 * The one place a new chat state is produced.
 *
 * Pure: same state and same intent always give the same result. No network, no clock, no reading a field from
 * anywhere else. That is what makes it testable and what makes an update atomic — a caller sends one intent and
 * gets one whole new state, so the screen cannot show a half-applied change.
 *
 * Each branch delegates to a rule in [ConnectionStateUpdates], [ComposerStateUpdates] or [TranscriptReducer].
 * Those rules answer "what changes, and what is deliberately kept". This answers only "which rule runs".
 */
object ChatReducer {
    fun reduce(
        state: ChatUiState,
        intent: ChatIntent,
    ): ChatUiState =
        when (intent) {
            // Connection
            ChatIntent.FirstConnected -> state.copy(connection = state.connection.afterFirstConnected())

            is ChatIntent.StatusPillChanged ->
                state.copy(connection = state.connection.afterStatusPill(intent.text))

            ChatIntent.StatusPillCleared -> state.copy(connection = state.connection.afterStatusPillCleared())

            // Composer
            is ChatIntent.SlashUsageCountsChanged ->
                state.copy(composer = state.composer.afterSlashUsageCounts(intent.counts))

            is ChatIntent.HistorySheetRequested ->
                state.copy(composer = state.composer.afterOpenHistoryRequest(intent.open))

            is ChatIntent.PrefillRequested ->
                state.copy(composer = state.composer.afterPendingPrefill(intent.text))

            ChatIntent.ProfileChanged -> state.copy(composer = state.composer.afterProfileChange())

            is ChatIntent.ModelContextMeasured ->
                state.copy(
                    composer = state.composer.afterModelContextLength(intent.length, intent.model),
                )

            is ChatIntent.TypingSettingsLoaded ->
                state.copy(composer = state.composer.afterTypingSettings(intent.enabled, intent.delayMs))

            is ChatIntent.SessionModelConfirmed ->
                state.copy(composer = state.composer.afterSessionModel(intent.label))

            is ChatIntent.ReasoningLevelChanged ->
                state.copy(composer = state.composer.afterReasoningLevel(intent.level))

            // Transcript: already an intent-driven slice.
            is ChatIntent.Transcript ->
                state.copy(transcript = TranscriptReducer.reduce(state.transcript, intent.intent))
        }
}
