package com.m57.hermescontrol.ui.chat

/**
 * The transcript slice: history, streaming, messages.
 *
 * `ChatHistory` produces [ChatHistoryUpdate] events; this writes them into state. The split is deliberate —
 * one class owns the asynchronous read and its counters and jobs, the other owns the state write and the
 * bookkeeping that a write implies. Before this, `ChatViewModel.applyHistoryUpdate` did both and was 121 lines.
 *
 * Every external fact arrives as a lambda, the same rule [ChatHistory] follows: nothing here reads a field it
 * does not own, so a test supplies a fake for each one.
 */
internal class TranscriptController(
    /** Applies a state transition and returns the new state, as `MutableStateFlow.update` does. */
    private val updateState: ((ChatUiState) -> ChatUiState) -> Unit,
    /** Where the next "load older" read starts. Owned by the caller because paging spans history and rail. */
    private val setMessageOffset: (Int) -> Unit,
    /** Whether the last load came back in the newer paging shape. */
    private val setLatestPaging: (Boolean) -> Unit,
    /** Message ids a cache load is still holding, so the authoritative load can retire them. */
    private val activeCacheMessageIds: () -> Set<String>,
    /** The transcript as it stands, for checks that must look at what is already displayed. */
    private val readMessages: () -> List<ChatMessage>,
    /** Ends the cache load. Returns nothing; what it held is read before this is called. */
    private val retireCacheLoad: () -> Unit,
    /** Todos recovered from the transcript, falling back to what is already there. */
    private val restoredTodos: (List<TodoItem>, List<ChatMessage>) -> List<TodoItem>,
    /** The clock, so a failure can be stamped without the controller owning time. */
    private val nowMs: () -> Long,
    /** Page size the reducer needs to decide whether a page is full. */
    private val pageSize: Int,
) {
    /**
     * Applies one history event to the transcript.
     *
     * [ChatHistoryUpdate.MessageCountKnown] is not handled here: it writes a session's message count into the
     * rail, which is not the transcript's slice. The caller keeps that one case.
     */
    fun apply(update: ChatHistoryUpdate) {
        when (update) {
            ChatHistoryUpdate.OlderStarted ->
                updateState { state ->
                    state.copy(
                        transcript = TranscriptReducer.reduce(state.transcript, TranscriptIntent.OlderLoadStarted),
                    )
                }

            is ChatHistoryUpdate.OlderLoaded -> {
                setMessageOffset(update.effectiveOffset)
                updateState { current ->
                    current.copy(
                        transcript =
                            TranscriptReducer.reduce(
                                current.transcript,
                                TranscriptIntent.OlderLoaded(
                                    older = update.older,
                                    useLatestPaging = update.useLatestPaging,
                                    returned = update.returned,
                                    effectiveOffset = update.effectiveOffset,
                                    limit = update.limit,
                                ),
                            ),
                    )
                }
            }

            ChatHistoryUpdate.LoadStarted ->
                updateState { state ->
                    state.copy(
                        transcript = TranscriptReducer.reduce(state.transcript, TranscriptIntent.LoadStarted),
                    )
                }

            is ChatHistoryUpdate.LoadSucceeded -> {
                val cachedIds = activeCacheMessageIds()
                retireCacheLoad()
                setLatestPaging(update.useLatestPaging)
                updateState { state ->
                    val hasResumeHistory = state.messages.any { it.origin is MessageOrigin.Resume }
                    state.copy(
                        transcript =
                            TranscriptReducer.reduce(
                                state.transcript,
                                TranscriptIntent.HistoryLoaded(
                                    authoritative = update.messages,
                                    baselineIds = update.baselineIds,
                                    cachedIds = cachedIds,
                                    hasResumeHistory = hasResumeHistory,
                                    useLatestPaging = update.useLatestPaging,
                                    returned = update.returned,
                                    offset = update.offset,
                                    pageSize = pageSize,
                                ),
                            ),
                    )
                }
                val hasRestHistory =
                    readMessages().any { it.origin is MessageOrigin.Rest }
                if (update.messages.isNotEmpty() && hasRestHistory) setMessageOffset(update.offset)
            }

            is ChatHistoryUpdate.LoadFailed ->
                updateState { state ->
                    state.copy(
                        transcript =
                            TranscriptReducer.reduce(
                                state.transcript,
                                TranscriptIntent.HistoryFailed(reason = update.reason, nowMs = nowMs()),
                            ),
                    )
                }

            is ChatHistoryUpdate.SyncMerged ->
                updateState { current ->
                    val merged =
                        mergeSyncedMessages(
                            current = current.messages,
                            incoming = update.incoming,
                        )
                    if (sameTranscript(current.messages, merged)) {
                        current
                    } else {
                        current.copy(
                            transcript =
                                current.transcript.copy(
                                    messages = merged,
                                    todos = restoredTodos(current.todos, merged),
                                ),
                        )
                    }
                }

            ChatHistoryUpdate.OlderFinished ->
                updateState { state ->
                    state.copy(transcript = state.transcript.copy(isLoadingOlder = false))
                }

            ChatHistoryUpdate.OlderSeamBroken ->
                updateState { state ->
                    state.copy(
                        transcript = TranscriptReducer.reduce(state.transcript, TranscriptIntent.OlderSeamBroken),
                    )
                }

            // Not the transcript's slice.
            is ChatHistoryUpdate.MessageCountKnown -> Unit
            ChatHistoryUpdate.LoadFinished -> Unit
            ChatHistoryUpdate.SyncStarted -> Unit
            ChatHistoryUpdate.SyncFinished -> Unit
        }
    }
}
