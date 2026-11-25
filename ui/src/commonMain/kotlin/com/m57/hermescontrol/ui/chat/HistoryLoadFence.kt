package com.m57.hermescontrol.ui.chat

/**
 * Everything a history read must still match when its result comes back, captured as one question.
 *
 * A page that arrives after the user switched session, switched profile, reconnected, or after the
 * transcript was replaced would append rows into a conversation they no longer describe. The fence is
 * captured before the request and asked again before every write: `isCurrent()` bundles the six live
 * signals (generation, profile, session, runtime session, transcript revision, socket binding) into a
 * single answer, so the history code never needs to know what those signals are.
 *
 * It used to be a private data class inside the ViewModel that every caller re-checked field by field.
 *
 * @param transcriptRevision the repository's replacement generation at capture time; the write path needs
 *   it to persist rows only into the transcript they were fetched for.
 */
internal class HistoryLoadFence(
    val sessionId: String,
    val transcriptRevision: Long,
    private val stillValid: () -> Boolean,
) {
    fun isCurrent(): Boolean = stillValid()
}
