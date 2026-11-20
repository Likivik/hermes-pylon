package com.m57.hermescontrol.ui.chat

/**
 * Transcript transitions as data, resolved by one pure function.
 *
 * The load path in `ChatViewModel` interleaves IO, job cancellation and generation fences with state
 * writes. Those concerns have to stay where they are — a pure function cannot decide whether a
 * response is stale. What it *can* own is the state arithmetic once the answer is known:
 *
 * ```
 * if (!isHistoryRefreshCurrent(fence, owner)) return   // concurrency, stays in the ViewModel
 * state = TranscriptReducer.reduce(state, HistoryLoaded(…))   // arithmetic, testable without coroutines
 * ```
 *
 * Keeping that boundary explicit is the point of the split: ordering bugs stay with the fences, and
 * state-shape bugs become unit tests.
 */
sealed interface TranscriptIntent {
    /** A full refresh began: the screen is loading again and the previous error is stale. */
    data object LoadStarted : TranscriptIntent

    /**
     * The authoritative page arrived and passed every fence.
     *
     * @param authoritative messages the server says are the current page
     * @param baselineIds ids already on screen *before* this load started. This is the only way to
     *   tell "a message that was already here" from "a message that arrived during the load": a
     *   message missing from this set survives the page by design.
     * @param cachedIds ids painted from the local cache during this load
     * @param hasResumeHistory whether the on-screen transcript contains resume placeholders
     * @param useLatestPaging whether the backend answered with `order=latest` paging
     * @param returned how many rows the server said it returned for this page
     * @param offset the page offset actually applied
     * @param pageSize the page size used to decide whether an older page may exist
     */
    data class HistoryLoaded(
        val authoritative: List<ChatMessage>,
        val baselineIds: Set<String>,
        val cachedIds: Set<String>,
        val hasResumeHistory: Boolean,
        val useLatestPaging: Boolean,
        val returned: Int,
        val offset: Int,
        val pageSize: Int,
    ) : TranscriptIntent

    /**
     * The refresh failed. Two different situations, deliberately two different outcomes: with content
     * on screen the transcript is still usable and gets a stale marker; with nothing on screen the
     * user gets an error.
     */
    data class HistoryFailed(
        val reason: String,
        val nowMs: Long,
    ) : TranscriptIntent

    /** A request for an older page began. */
    data object OlderLoadStarted : TranscriptIntent

    /**
     * An older page arrived and passed every fence: prepend it, drop ids that are already on screen.
     *
     * @param older the page, oldest-first as the server returned it
     * @param limit the page size requested, used to decide whether another page may exist
     * @param effectiveOffset the offset the server actually applied
     */
    data class OlderLoaded(
        val older: List<ChatMessage>,
        val useLatestPaging: Boolean,
        val returned: Int,
        val effectiveOffset: Int,
        val limit: Int,
    ) : TranscriptIntent
}

object TranscriptReducer {
    fun reduce(
        state: TranscriptState,
        intent: TranscriptIntent,
    ): TranscriptState =
        when (intent) {
            TranscriptIntent.LoadStarted ->
                state.copy(isLoading = true, errorMessage = null)

            is TranscriptIntent.HistoryLoaded -> reduceLoaded(state, intent)

            is TranscriptIntent.HistoryFailed -> reduceFailed(state, intent)

            TranscriptIntent.OlderLoadStarted ->
                state.copy(isLoadingOlder = true)

            is TranscriptIntent.OlderLoaded -> reduceOlderLoaded(state, intent)
        }

    private fun reduceLoaded(
        state: TranscriptState,
        intent: TranscriptIntent.HistoryLoaded,
    ): TranscriptState {
        // A resume placeholder is not history: when the server has nothing for this session yet, the
        // placeholder must survive instead of being replaced by an empty page.
        if (intent.authoritative.isEmpty() && intent.hasResumeHistory) {
            return state.copy(isLoading = false, isLoadingOlder = false, staleSince = null)
        }

        val authoritativeIds = intent.authoritative.mapTo(mutableSetOf()) { it.id }
        // Messages that arrived while this load was in flight (streaming, optimistic sends) are newer
        // than the page and must not be dropped by it. Messages from the baseline or the cache are
        // already represented by the authoritative page.
        val newerMessages =
            state.messages.filter { message ->
                message.id !in intent.baselineIds &&
                    message.id !in intent.cachedIds &&
                    message.id !in authoritativeIds
            }
        val refreshed = intent.authoritative + newerMessages
        val hasOlder =
            if (intent.useLatestPaging) {
                intent.returned >= intent.pageSize && intent.authoritative.isNotEmpty()
            } else {
                intent.offset > 0 && intent.authoritative.isNotEmpty()
            }
        return state.copy(
            messages = refreshed,
            todos = hydrateTodosFromMessages(refreshed).ifEmpty { state.todos },
            isLoading = false,
            hasOlderMessages = hasOlder,
            // The server just spoke: whatever was stale is now current.
            isLoadingOlder = false,
            staleSince = null,
        )
    }

    private fun reduceOlderLoaded(
        state: TranscriptState,
        intent: TranscriptIntent.OlderLoaded,
    ): TranscriptState {
        // Prepend, then dedupe by id: a page boundary can repeat a message that is already on screen.
        val merged = (intent.older + state.messages).distinctBy { it.id }
        val hasOlder =
            if (intent.useLatestPaging) {
                intent.returned >= intent.limit && intent.older.isNotEmpty()
            } else {
                intent.effectiveOffset > 0 && intent.older.isNotEmpty()
            }
        return state.copy(
            messages = merged,
            todos = hydrateTodosFromMessages(merged).ifEmpty { state.todos },
            hasOlderMessages = hasOlder,
        )
    }

    private fun reduceFailed(
        state: TranscriptState,
        intent: TranscriptIntent.HistoryFailed,
    ): TranscriptState =
        if (state.messages.isEmpty()) {
            state.copy(
                isLoading = false,
                isLoadingOlder = false,
                errorMessage = "Failed to load messages: ${intent.reason}",
            )
        } else {
            state.copy(
                isLoading = false,
                isLoadingOlder = false,
                errorMessage = null,
                // Keep the FIRST failure time: a repeated refresh should not keep resetting how long
                // the user has been looking at possibly-stale content.
                staleSince = state.staleSince ?: intent.nowMs,
            )
        }
}
