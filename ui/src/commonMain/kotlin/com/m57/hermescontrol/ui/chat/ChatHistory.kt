package com.m57.hermescontrol.ui.chat

import com.m57.hermescontrol.data.model.SessionMessage
import com.m57.hermescontrol.data.model.SessionMessagesResponse
import com.m57.hermescontrol.data.remote.NetworkResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext

/**
 * One change the history area wants made to the transcript.
 *
 * The class below never touches `_uiState`. It decides *what* happened; the ViewModel decides what that
 * means for the screen. That is the whole point of the seam: the ordering rules become testable without a
 * live socket, and a page that loses its race is a value the reducer ignores rather than a field poked
 * from inside a coroutine.
 */
internal sealed interface ChatHistoryUpdate {
    data object OlderStarted : ChatHistoryUpdate

    data class OlderLoaded(
        val older: List<ChatMessage>,
        val useLatestPaging: Boolean,
        val returned: Int,
        val effectiveOffset: Int,
        val limit: Int,
    ) : ChatHistoryUpdate

    data object OlderFinished : ChatHistoryUpdate
}

/**
 * The "load older" read, with its own counters and job.
 *
 * This is the first slice that genuinely *owns* state — `olderLoadCounter`, the active owner token and the
 * job handle all live here now. Extension-function extraction never worked for this area for exactly that
 * reason; a class with explicit inputs does, because nothing it needs is read from a field it does not own.
 *
 * Every external fact arrives as a lambda: fetching, mapping, persisting, capturing the fence, and checking
 * that the fence still holds. None of them are fields, so a test can supply a fake for each one.
 */
internal class ChatHistory(
    private val io: CoroutineDispatcher,
    private val launch: (suspend () -> Unit) -> Job,
    private val fetchPage: suspend (
        sessionId: String,
        offset: Int,
        limit: Int,
        order: String?,
    ) -> NetworkResult<SessionMessagesResponse>,
    private val mapMessages: (
        sessionId: String,
        raw: List<SessionMessage>,
        offset: Int,
        latest: Boolean,
    ) -> List<ChatMessage>,
    private val persist: suspend (
        messages: List<ChatMessage>,
        sessionId: String,
        revision: Long,
        stillCurrent: () -> Boolean,
    ) -> Boolean,
    private val captureFence: (sessionId: String) -> HistoryLoadFence?,
    private val write: (ChatHistoryUpdate) -> Unit,
) {
    private var olderCounter = 0L
    private var activeOlderOwner: Long? = null
    private var olderJob: Job? = null

    fun loadOlder(
        sessionId: String,
        hasOlderMessages: Boolean,
        isLoadingOlder: Boolean,
        latestPaging: Boolean,
        loadedOffset: Int,
        pageSize: Int,
    ) {
        val request =
            planOlderLoad(
                hasOlderMessages = hasOlderMessages,
                isLoadingOlder = isLoadingOlder,
                latestPaging = latestPaging,
                loadedOffset = loadedOffset,
                pageSize = pageSize,
            ) ?: return
        val fence = captureFence(sessionId) ?: return

        val owner = ++olderCounter
        activeOlderOwner = owner
        write(ChatHistoryUpdate.OlderStarted)

        olderJob =
            launch {
                try {
                    val result =
                        fetchPage(
                            sessionId,
                            request.offset,
                            request.limit,
                            if (request.useLatestPaging) "latest" else null,
                        )
                    if (result !is NetworkResult.Success || !isCurrent(fence, owner)) return@launch
                    val effectiveOffset = result.data.pagination?.offset ?: request.offset
                    val older =
                        mapMessages(
                            sessionId,
                            result.data.messages.orEmpty(),
                            effectiveOffset,
                            request.useLatestPaging,
                        )
                    val persisted =
                        withContext(io) {
                            persist(older, sessionId, fence.transcriptRevision) { isCurrent(fence, owner) }
                        }
                    if (!persisted || !isCurrent(fence, owner)) return@launch
                    write(
                        ChatHistoryUpdate.OlderLoaded(
                            older = older,
                            useLatestPaging = request.useLatestPaging,
                            returned = result.data.pagination?.returned ?: older.size,
                            effectiveOffset = effectiveOffset,
                            limit = request.limit,
                        ),
                    )
                } finally {
                    release(owner)
                }
            }
    }

    /** Cancels the in-flight older load, if any. Used when the session is replaced under it. */
    fun retireOlder() {
        val owner = activeOlderOwner ?: return
        if (activeOlderOwner != owner) return
        activeOlderOwner = null
        olderJob?.cancel()
        olderJob = null
        write(ChatHistoryUpdate.OlderFinished)
    }

    private fun isCurrent(
        fence: HistoryLoadFence,
        owner: Long,
    ): Boolean = activeOlderOwner == owner && fence.isCurrent()

    private fun release(owner: Long) {
        if (activeOlderOwner != owner) return
        activeOlderOwner = null
        olderJob = null
        write(ChatHistoryUpdate.OlderFinished)
    }
}
