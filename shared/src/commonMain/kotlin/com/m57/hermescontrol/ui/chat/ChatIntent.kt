package com.m57.hermescontrol.ui.chat

/**
 * Everything that can happen to the chat screen.
 *
 * One case per named rule the screen already has. The rules in [ConnectionStateUpdates] and
 * [ComposerStateUpdates] answer "what changes, and what is deliberately kept"; this answers "what happened".
 * Sending an intent is how a caller says something happened, instead of reaching in and setting a field.
 *
 * This is additive. Nothing routes through it yet — [ChatIntent.Transcript] is the only case with a live
 * producer, and it wraps the slice that already worked this way.
 */
sealed interface ChatIntent {
    // ── Connection: [ConnectionStateUpdates] ─────────────────────────────

    /** A connect succeeded for the first time. Rule: [ConnectionState.afterFirstConnected]. */
    data object FirstConnected : ChatIntent

    /** The status pill should show some text. Rule: [ConnectionState.afterStatusPill]. */
    data class StatusPillChanged(val text: String) : ChatIntent

    /** The status pill should go away. Rule: [ConnectionState.afterStatusPillCleared]. */
    data object StatusPillCleared : ChatIntent

    // ── Composer: [ComposerStateUpdates] ────────────────────────────────

    /** Slash-command usage counts arrived. Rule: [ComposerState.afterSlashUsageCounts]. */
    data class SlashUsageCountsChanged(val counts: Map<String, Int>) : ChatIntent

    /** The history sheet was asked for, or the ask was spent. Rule: [ComposerState.afterOpenHistoryRequest]. */
    data class HistorySheetRequested(val open: Boolean) : ChatIntent

    /** Text should appear in the composer without the user typing it. Rule: [ComposerState.afterPendingPrefill]. */
    data class PrefillRequested(val text: String?) : ChatIntent

    /** The account changed, so the profile-bound fields go. Rule: [ComposerState.afterProfileChange]. */
    data object ProfileChanged : ChatIntent

    /** A model's context window was measured. Rule: [ComposerState.afterModelContextLength]. */
    data class ModelContextMeasured(
        val length: Long,
        val model: String?,
    ) : ChatIntent

    /** The typing effect and its delay arrived from settings. Rule: [ComposerState.afterTypingSettings]. */
    data class TypingSettingsLoaded(
        val enabled: Boolean,
        val delayMs: Int,
    ) : ChatIntent

    /** The session confirmed its model. Rule: [ComposerState.afterSessionModel]. */
    data class SessionModelConfirmed(val label: String?) : ChatIntent

    /** The reasoning level changed. Rule: [ComposerState.afterReasoningLevel]. */
    data class ReasoningLevelChanged(val level: String?) : ChatIntent

    // ── Transcript ──────────────────────────────────────────────────────

    /**
     * Something happened to the transcript.
     *
     * This slice already works by intent — [TranscriptReducer] has taken a [TranscriptIntent] since it was
     * extracted. Wrapping rather than flattening keeps that reducer's public surface untouched.
     */
    data class Transcript(val intent: TranscriptIntent) : ChatIntent
}
