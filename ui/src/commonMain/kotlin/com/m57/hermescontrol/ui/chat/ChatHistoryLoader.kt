package com.m57.hermescontrol.ui.chat

import com.m57.hermescontrol.data.model.SessionMessagesResponse
import com.m57.hermescontrol.data.remote.NetworkResult
import com.m57.hermescontrol.data.remote.safeApiCall
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * The two gateway reads the history path needs, without the ViewModel around them.
 *
 * This is Batch 3's first slice, and deliberately the smallest one that can be moved: both calls take
 * only a session id and return a value. They hold no fences, no counters and no jobs, which is exactly
 * why they can leave first — the entangled parts of history (refresh generations, owner tokens, job
 * retirement) stay where they are until they get a real seam.
 *
 * The reason this exists at all: history had no unit test for "ask for the right page", because asking
 * went through a private ViewModel method that also wrote state. Now a fake `ChatGateway` can answer.
 */
internal class ChatHistoryLoader(
    private val api: ChatGateway,
    private val io: CoroutineDispatcher,
) {
    /**
     * One page of messages.
     *
     * `order = "latest"` returns the newest page, which is what the first load wants; `null` lets the
     * gateway pick its default ordering, which is what older-page loads use.
     */
    suspend fun page(
        sessionId: String,
        offset: Int,
        limit: Int,
        order: String? = null,
    ): NetworkResult<SessionMessagesResponse> =
        withContext(io) {
            safeApiCall {
                api.hermesApi.getSessionMessages(
                    sessionId = sessionId,
                    limit = limit,
                    offset = offset,
                    // Compacted messages are part of the visible history; excluding them made the
                    // transcript shorter than the gateway's own count.
                    includeCompacted = true,
                    order = order,
                )
            }
        }

    /**
     * The gateway's own message count for a session, or null when it does not report one.
     *
     * One precise row rather than a listing: the sessions route caps `limit` at 100, so the older
     * `getSessions(limit = 500)` here could never succeed.
     */
    suspend fun messageCount(sessionId: String): Int? =
        withContext(io) {
            val result = safeApiCall { api.hermesApi.getSession(sessionId) }
            (result as? NetworkResult.Success)?.data?.message_count
        }
}
