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

    data object SyncStarted : ChatHistoryUpdate

    data class SyncMerged(val sessionId: String, val incoming: List<ChatMessage>) : ChatHistoryUpdate

    data object SyncFinished : ChatHistoryUpdate

    data class MessageCountKnown(val sessionId: String, val count: Int) : ChatHistoryUpdate

    data object LoadStarted : ChatHistoryUpdate

    data class LoadSucceeded(
        val sessionId: String,
        val messages: List<ChatMessage>,
        val offset: Int,
        val useLatestPaging: Boolean,
        val returned: Int,
        val baselineIds: Set<String>,
    ) : ChatHistoryUpdate

    data class LoadFailed(val reason: String) : ChatHistoryUpdate

    data object LoadFinished : ChatHistoryUpdate
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

    private var refreshCounter = 0L
    private var activeRefreshOwner: Long? = null
    private var refreshJob: Job? = null

    private var syncCounter = 0L
    private var activeSyncOwner: Long? = null
    private var syncJob: Job? = null

    /** True while a sync is in flight; the guard for "do not start another". */
    val isSyncing: Boolean get() = activeSyncOwner != null

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
                    if (result !is NetworkResult.Success || !isOlderCurrent(owner, fence)) return@launch
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
                            persist(older, sessionId, fence.transcriptRevision) { isOlderCurrent(owner, fence) }
                        }
                    if (!persisted || !isOlderCurrent(owner, fence)) return@launch
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

    /** Continues the transcript from where it already is, without touching what the user is reading. */
    fun sync(
        sessionId: String,
        isLoading: Boolean,
        isLoadingOlder: Boolean,
        isAgentTyping: Boolean,
        isStreaming: Boolean,
        latestPaging: Boolean,
        serverIndexes: List<Int>,
        loadedOffset: Int,
        pageSize: Int,
    ) {
        val request =
            planSync(
                isSyncing = isSyncing,
                isLoading = isLoading,
                isLoadingOlder = isLoadingOlder,
                isAgentTyping = isAgentTyping,
                isStreaming = isStreaming,
                latestPaging = latestPaging,
                serverIndexes = serverIndexes,
                loadedOffset = loadedOffset,
                pageSize = pageSize,
            ) ?: return
        val fence = captureFence(sessionId) ?: return

        val owner = ++syncCounter
        activeSyncOwner = owner
        write(ChatHistoryUpdate.SyncStarted)

        syncJob =
            launch {
                try {
                    val result =
                        fetchPage(
                            sessionId,
                            request.offset,
                            request.limit,
                            if (request.useLatestPaging) "latest" else null,
                        )
                    if (result !is NetworkResult.Success || !isSyncCurrent(owner, fence)) return@launch
                    val incoming =
                        mapMessages(
                            sessionId,
                            result.data.messages.orEmpty(),
                            request.offset,
                            request.useLatestPaging,
                        )
                    if (incoming.isEmpty() || !isSyncCurrent(owner, fence)) return@launch
                    val persisted =
                        withContext(io) {
                            persist(incoming, sessionId, fence.transcriptRevision) { isSyncCurrent(owner, fence) }
                        }
                    if (!persisted || !isSyncCurrent(owner, fence)) return@launch
                    write(ChatHistoryUpdate.SyncMerged(sessionId, incoming))
                } finally {
                    releaseSync(owner)
                }
            }
    }

    /** Cancels an in-flight sync, e.g. when the session is replaced under it. */
    fun retireSync() {
        if (activeSyncOwner == null) return
        activeSyncOwner = null
        syncCounter++
        syncJob?.cancel()
        syncJob = null
        write(ChatHistoryUpdate.SyncFinished)
    }

    private fun releaseSync(owner: Long) {
        if (activeSyncOwner != owner) return
        activeSyncOwner = null
        syncJob = null
        write(ChatHistoryUpdate.SyncFinished)
    }

    /**
     * The authoritative load for one session: probe the newest page, decide where to start, fetch it.
     *
     * A refresh supersedes any incremental sync or older-page load, so those are retired first — before the
     * persistence barrier and the fence capture — otherwise a queued write could become authoritative after
     * it.
     */
    fun refresh(
        sessionId: String,
        baselineIds: Set<String>,
        pageSize: Int,
        awaitSessionOperations: suspend (String) -> Unit,
        countProvider: suspend () -> Int?,
    ) {
        retireSync()
        retireOlder()
        val owner = ++refreshCounter
        activeRefreshOwner = owner
        refreshJob?.cancel()
        write(ChatHistoryUpdate.LoadStarted)

        refreshJob =
            launch {
                try {
                    withContext(io) { awaitSessionOperations(sessionId) }
                    if (activeRefreshOwner != owner) return@launch
                    val fence = captureFence(sessionId) ?: return@launch
                    if (!isRefreshCurrent(fence, owner)) return@launch

                    val probe = fetchPage(sessionId, 0, pageSize, "latest")
                    if (!isRefreshCurrent(fence, owner)) return@launch
                    val probeOrder = (probe as? NetworkResult.Success)?.data?.pagination?.order
                    val messageCount =
                        if (probeOrder != "latest") {
                            countProvider() ?: return@launch
                        } else {
                            null
                        }
                    if (!isRefreshCurrent(fence, owner)) return@launch

                    val plan =
                        planFirstLoad(
                            probeSucceeded = probe is NetworkResult.Success,
                            probeOrder = probeOrder,
                            messageCount = messageCount,
                            pageSize = pageSize,
                        ) ?: return@launch

                    val result =
                        when {
                            plan.useLatestPaging -> probe
                            probe is NetworkResult.Failure -> probe
                            else -> fetchPage(sessionId, plan.offset, pageSize, null)
                        }
                    if (!isRefreshCurrent(fence, owner)) return@launch

                    when (result) {
                        is NetworkResult.Success -> {
                            val offset = result.data.pagination?.offset ?: plan.offset
                            val mapped =
                                mapMessages(
                                    sessionId,
                                    result.data.messages.orEmpty(),
                                    offset,
                                    plan.useLatestPaging,
                                )
                            val persisted =
                                withContext(io) {
                                    persist(mapped, sessionId, fence.transcriptRevision) {
                                        isRefreshCurrent(fence, owner)
                                    }
                                }
                            if (!persisted || !isRefreshCurrent(fence, owner)) return@launch
                            write(
                                ChatHistoryUpdate.LoadSucceeded(
                                    sessionId = sessionId,
                                    messages = mapped,
                                    offset = offset,
                                    useLatestPaging = plan.useLatestPaging,
                                    returned = result.data.pagination?.returned ?: mapped.size,
                                    baselineIds = baselineIds,
                                ),
                            )
                        }

                        is NetworkResult.Failure -> write(ChatHistoryUpdate.LoadFailed(result.error.message))
                    }
                } finally {
                    releaseRefresh(owner)
                }
            }
    }

    /** Cancels the in-flight refresh, e.g. when the session is switched under it. */
    fun retireRefresh() {
        activeRefreshOwner = null
        refreshCounter++
        refreshJob?.cancel()
        refreshJob = null
        write(ChatHistoryUpdate.LoadFinished)
    }

    private fun releaseRefresh(owner: Long) {
        if (activeRefreshOwner != owner) return
        activeRefreshOwner = null
        refreshJob = null
        write(ChatHistoryUpdate.LoadFinished)
    }

    private fun isRefreshCurrent(
        fence: HistoryLoadFence,
        owner: Long,
    ): Boolean = activeRefreshOwner == owner && fence.isCurrent()

    /**
     * How many messages the session has, preferring a number the client already knows.
     *
     * The rail usually knows the count, so most calls cost nothing. When it does not, one precise
     * `getSession` is asked — not a session listing, whose `limit` the route caps at 100. When even that
     * fails, the transcript's own size is the honest fallback: it is what the user can actually see.
     */
    suspend fun serverMessageCount(
        sessionId: String,
        knownCount: Int?,
        transcriptSize: Int,
        isCurrent: () -> Boolean,
        fetchCount: suspend (String) -> Int?,
    ): Int? {
        if (!isCurrent()) return null
        if (knownCount != null) return knownCount.takeIf { isCurrent() }
        val fetched = fetchCount(sessionId)
        if (fetched != null) {
            if (!isCurrent()) return null
            write(ChatHistoryUpdate.MessageCountKnown(sessionId, fetched))
            return fetched.takeIf { isCurrent() }
        }
        return transcriptSize.takeIf { isCurrent() }
    }

    private fun isOlderCurrent(
        owner: Long,
        fence: HistoryLoadFence,
    ): Boolean = activeOlderOwner == owner && fence.isCurrent()

    private fun isSyncCurrent(
        owner: Long,
        fence: HistoryLoadFence,
    ): Boolean = activeSyncOwner == owner && fence.isCurrent()

    private fun release(owner: Long) {
        if (activeOlderOwner != owner) return
        activeOlderOwner = null
        olderJob = null
        write(ChatHistoryUpdate.OlderFinished)
    }
}
