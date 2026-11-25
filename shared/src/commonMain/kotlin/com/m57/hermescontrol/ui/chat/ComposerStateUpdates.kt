package com.m57.hermescontrol.ui.chat

/*
 * Named state transforms for the composer.
 *
 * The composer fields were updated inline from a dozen call sites, each one spelling out which fields it
 * owned. That hid the rules: which fields a caller may change, which fields it must leave alone, and which
 * two actions share the same field but not the same meaning. The rules live here now, each in one named
 * function, each tested.
 *
 * Every function returns a new [ComposerState]. None of them mutate.
 */

/**
 * Rule: the slash-command usage counts are replaced wholesale.
 *
 * The counts are read from the store and are a snapshot of usage, not a delta. A caller that merges them
 * would resurrect counts the store has dropped.
 *
 * **Set:** [ComposerState.slashUsageCounts].
 *
 * **Kept on purpose:** every other composer field. Refreshing usage counts must not disturb the draft, the
 * attachments, the chosen model or the typing preferences.
 */
fun ComposerState.afterSlashUsageCounts(counts: Map<String, Int>): ComposerState = copy(slashUsageCounts = counts)

/**
 * Rule: the open-history request is a one-shot flag. It is raised when a slash command asks for the
 * history sheet and lowered again once the sheet has opened.
 *
 * **Set:** [ComposerState.openHistoryRequested].
 *
 * **Kept on purpose:** every other composer field. Raising the flag must not clear the draft the user is
 * typing, and lowering it must not either.
 */
fun ComposerState.afterOpenHistoryRequest(open: Boolean): ComposerState = copy(openHistoryRequested = open)

/**
 * Rule: a pending prefill is set only when it has visible text, and it is cleared by consuming it.
 *
 * A blank prefill carries nothing to show, so it is stored as absent. The draft the user typed is a
 * separate field and survives both the set and the clear.
 *
 * **Set:** [ComposerState.pendingPrefillText].
 *
 * **Kept on purpose:** the attachments, the reasoning level, the chosen model and the typing preferences.
 * A prefill is one message of text, not a replacement for the composer's contents.
 */
fun ComposerState.afterPendingPrefill(text: String?): ComposerState =
    copy(pendingPrefillText = text?.takeIf { it.isNotBlank() })

/**
 * Rule: a profile change drops the session-bound model and any pending prefill.
 *
 * Both belonged to the profile that just went away. The confirmed model of the new profile is applied
 * separately by [afterSessionModel].
 *
 * **Cleared:** [ComposerState.currentSessionModel], [ComposerState.pendingPrefillText].
 *
 * **Kept on purpose:** attachments, slash-command usage counts, the reasoning level, the terminal backend
 * and the typing preferences. Those are the user's own settings, not the old profile's session.
 */
fun ComposerState.afterProfileChange(): ComposerState =
    copy(
        currentSessionModel = null,
        pendingPrefillText = null,
    )

/**
 * Rule: the context length and the model it was measured for are set together.
 *
 * The length alone is misleading — it belongs to one qualified model name. The model name is stored
 * beside it so a stale length can be recognised and discarded.
 *
 * **Set:** [ComposerState.modelContextLength], [ComposerState.modelContextLengthModel].
 *
 * **Kept on purpose:** every other composer field. Measuring the model changes nothing the user can see
 * in the composer.
 */
fun ComposerState.afterModelContextLength(
    length: Long,
    model: String?,
): ComposerState =
    copy(
        modelContextLength = length,
        modelContextLengthModel = model,
    )

/**
 * Rule: the typing effect and its delay come from the settings store and are applied as a pair.
 *
 * The delay is meaningless without the effect, so one function sets both.
 *
 * **Set:** [ComposerState.typingEffectEnabled], [ComposerState.typingEffectDelayMs].
 *
 * **Kept on purpose:** the draft, the attachments, the prefill and the model choice. The typing effect is
 * how text is drawn, not what is being written.
 */
fun ComposerState.afterTypingSettings(
    enabled: Boolean,
    delayMs: Int,
): ComposerState =
    copy(
        typingEffectEnabled = enabled,
        typingEffectDelayMs = delayMs,
    )

/**
 * Rule: a confirmed session model replaces the current one. A null confirmation keeps the current model.
 *
 * The confirmation can be absent when the session reports no model of its own. In that case the last
 * known model is still the best answer, so it stays.
 *
 * **Set:** [ComposerState.currentSessionModel].
 *
 * **Kept on purpose:** the draft, the attachments, the prefill and the typing preferences.
 */
fun ComposerState.afterSessionModel(label: String?): ComposerState =
    copy(currentSessionModel = label ?: currentSessionModel)

/**
 * Rule: the reasoning level is replaced by the level the user picked.
 *
 * **Set:** [ComposerState.reasoningLevel].
 *
 * **Kept on purpose:** every other composer field, including the model choice. Picking a reasoning level
 * does not switch the model.
 */
fun ComposerState.afterReasoningLevel(level: String?): ComposerState = copy(reasoningLevel = level)
