package com.m57.hermescontrol.ui.chat

/**
 * The composer slice's half of state changes: the draft's surroundings — model, typing preferences, prefill,
 * history, reasoning.
 *
 * Pure and total. Every branch delegates to a rule in [ComposerStateUpdates], which answers "what changes and
 * what is deliberately kept"; this answers only "which rule runs". Intents that are not about the composer are
 * ignored here and answered by another slice.
 */
object ComposerReducer {
    fun reduce(
        state: ComposerState,
        intent: ChatIntent,
    ): ComposerState =
        when (intent) {
            is ChatIntent.SlashUsageCountsChanged -> state.afterSlashUsageCounts(intent.counts)

            is ChatIntent.HistorySheetRequested -> state.afterOpenHistoryRequest(intent.open)

            is ChatIntent.PrefillRequested -> state.afterPendingPrefill(intent.text)

            ChatIntent.ProfileChanged -> state.afterProfileChange()

            is ChatIntent.ModelContextMeasured -> state.afterModelContextLength(intent.length, intent.model)

            is ChatIntent.TypingSettingsLoaded -> state.afterTypingSettings(intent.enabled, intent.delayMs)

            is ChatIntent.SessionModelConfirmed -> state.afterSessionModel(intent.label)

            is ChatIntent.ReasoningLevelChanged -> state.afterReasoningLevel(intent.level)

            // Not this slice's business.
            else -> state
        }
}
