package com.m57.hermescontrol.ui.chat

import com.m57.hermescontrol.data.model.SessionInfo
import com.m57.hermescontrol.data.model.SessionListResponse
import com.m57.hermescontrol.data.remote.NetworkError
import com.m57.hermescontrol.data.remote.NetworkResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job

/** What a rail refresh wants the screen to do. */
internal sealed interface ChatRailUpdate {
    data class Loaded(
        val sessions: List<SessionUi>,
        val rows: Int,
        val pages: Int,
        val elapsedMs: Long,
    ) : ChatRailUpdate

    data class Failed(val error: NetworkError, val backoffMs: Long) : ChatRailUpdate
}

/**
 * The session rail's refresh cycle: coalescing, rate limiting, paging and backoff.
 *
 * The rail owns its own lifecycle — its own in-flight flag, its own token, its own failure counter and its
 * own next-allowed timestamp — which is why it was given a separate sub-state before the transcript got one.
 * That ownership is exactly what made it impossible to cut out as extension functions: the cut took the
 * counters with it. As a class with explicit inputs it is ordinary code.
 *
 * Two rules here are load-bearing and were previously only visible by using the app:
 *  - a forced refresh that arrives mid-flight is *remembered*, not dropped, because a dropped pull-to-refresh
 *    leaves the spinner on until the next signal; and
 *  - paging stops at a page cap, because the route caps `limit` at 100 and silently showing 100 of 400
 *    sessions is a regression against the RPC's 200.
 */
internal class ChatRail(
    private val io: CoroutineDispatcher,
    private val now: () -> Long,
    private val launch: (suspend () -> Unit) -> Job,
    private val fetchPage: suspend (offset: Int, limit: Int) -> NetworkResult<SessionListResponse>,
    private val selectSessions: (List<SessionInfo>) -> List<SessionUi>,
    private val backoffMs: (Int) -> Long,
    private val write: (ChatRailUpdate) -> Unit,
    private val pageSize: Int,
    private val maxPages: Int,
    private val minIntervalMs: Long,
) {
    private var seq = 0L
    private var inFlight = false
    private var pendingForce = false
    private var failures = 0
    private var nextAllowedAt = 0L

    /** @param force true for a user-initiated refresh (pull to refresh, app foregrounded). */
    fun request(force: Boolean) {
        if (inFlight) {
            // Dropping this would keep the pull-to-refresh spinner on until the next signal; queueing a
            // burst would hammer the route. One coalesced follow-up is the honest middle.
            if (force) pendingForce = true
            return
        }
        val nowMs = now()
        if (!force && nowMs < nextAllowedAt) return
        inFlight = true
        val token = ++seq
        launch {
            try {
                fetch(token)
            } finally {
                inFlight = false
                if (pendingForce) {
                    pendingForce = false
                    request(force = true)
                }
            }
        }
    }

    private suspend fun fetch(token: Long) {
        val started = now()
        val rows = ArrayList<SessionInfo>()
        var offset = 0
        var pages = 0
        var failure: NetworkError? = null

        while (pages < maxPages) {
            when (val result = fetchPage(offset, pageSize)) {
                is NetworkResult.Success -> {
                    val page = result.data.sessions.orEmpty()
                    pages++
                    rows += page
                    offset += page.size
                    if (page.size < pageSize || rows.size >= result.data.total) break
                }

                is NetworkResult.Failure -> {
                    failure = result.error
                    break
                }
            }
        }

        if (failure != null) {
            failures++
            val backoff = backoffMs(failures)
            nextAllowedAt = now() + backoff
            write(ChatRailUpdate.Failed(error = failure, backoffMs = backoff))
            return
        }

        if (token != seq) return
        val sessions = selectSessions(rows)
        failures = 0
        nextAllowedAt = now() + minIntervalMs
        write(ChatRailUpdate.Loaded(sessions = sessions, rows = rows.size, pages = pages, elapsedMs = now() - started))
    }
}
