package com.m57.hermescontrol.ui.chat

import com.m57.hermescontrol.data.model.SessionInfo
import com.m57.hermescontrol.data.model.SessionListResponse
import com.m57.hermescontrol.data.remote.NetworkError
import com.m57.hermescontrol.data.remote.NetworkResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The rail's refresh cycle without a socket: coalescing, rate limiting, paging and backoff.
 *
 * These rules were the ones only observable by using the app: a dropped pull-to-refresh leaves the spinner
 * on, and uncapped paging silently shows 100 of 400 sessions.
 */
class ChatRailTest {
    private fun rows(
        from: Int,
        count: Int,
    ) = (from until from + count).map { SessionInfo(id = "s$it") }

    private fun rail(
        scope: CoroutineScope,
        clock: () -> Long,
        updates: MutableList<ChatRailUpdate>,
        fetch: suspend (Int, Int) -> NetworkResult<SessionListResponse>,
        pageSize: Int = 100,
        maxPages: Int = 3,
        minIntervalMs: Long = 10_000L,
    ) = ChatRail(
        io = StandardTestDispatcher(),
        now = clock,
        launch = { block -> scope.launch { block() } },
        fetchPage = fetch,
        selectSessions = { list -> list.map { SessionUi(id = it.id, title = it.title ?: it.id) } },
        backoffMs = { failures -> (10_000L shl (failures - 1).coerceIn(0, 5)).coerceAtMost(300_000L) },
        write = { updates += it },
        pageSize = pageSize,
        maxPages = maxPages,
        minIntervalMs = minIntervalMs,
    )

    @Test
    fun aSuccessfulRefreshReportsTheRowsItLoaded() =
        runTest {
            var clock = 0L
            val updates = mutableListOf<ChatRailUpdate>()
            val rail =
                rail(this, { clock }, updates, { offset, _ ->
                    NetworkResult.Success(SessionListResponse(sessions = rows(offset, 2), total = 2))
                })

            rail.request(force = true)
            advanceUntilIdle()

            val loaded = updates.filterIsInstance<ChatRailUpdate.Loaded>().single()
            assertEquals(listOf("s0", "s1"), loaded.sessions.map { it.id })
            assertEquals(1, loaded.pages)
            assertTrue(clock >= 0)
        }

    @Test
    fun pagingStopsAtThePageCapEvenWhenMoreRowsExist() =
        runTest {
            val clock = { 0L }
            val updates = mutableListOf<ChatRailUpdate>()
            var calls = 0
            val rail =
                rail(this, clock, updates, { offset, pageSize ->
                    calls += 1
                    // Always a full page, and a total far beyond it: only the cap can stop this.
                    NetworkResult.Success(SessionListResponse(sessions = rows(offset, pageSize), total = 10_000))
                })

            rail.request(force = true)
            advanceUntilIdle()

            assertEquals(3, calls, "the route caps limit at 100, so the cap is what protects the rail")
        }

    @Test
    fun aForcedRefreshDuringAFlightIsCoalescedIntoOneFollowUp() =
        runTest {
            val clock = { 0L }
            val updates = mutableListOf<ChatRailUpdate>()
            val gate = CompletableDeferred<Unit>()
            var calls = 0
            val rail =
                rail(this, clock, updates, { _, _ ->
                    calls += 1
                    if (calls == 1) gate.await()
                    NetworkResult.Success(SessionListResponse(sessions = rows(0, 1), total = 1))
                })

            rail.request(force = true)
            rail.request(force = true)
            rail.request(force = true)
            advanceUntilIdle()
            gate.complete(Unit)
            advanceUntilIdle()

            assertEquals(2, calls, "three forced requests during one flight must become exactly one follow-up")
        }

    @Test
    fun anUnforcedRefreshInsideTheIntervalDoesNothing() =
        runTest {
            var clock = 0L
            val updates = mutableListOf<ChatRailUpdate>()
            var calls = 0
            val rail =
                rail(this, { clock }, updates, { _, _ ->
                    calls += 1
                    NetworkResult.Success(SessionListResponse(sessions = rows(0, 1), total = 1))
                })

            rail.request(force = true)
            advanceUntilIdle()
            clock = 5_000
            rail.request(force = false)
            advanceUntilIdle()

            assertEquals(1, calls, "a signal inside the quiet period must not hit the route")
        }

    @Test
    fun aFailureBacksOffAndTheNextAttemptWaitsLonger() =
        runTest {
            var clock = 0L
            val updates = mutableListOf<ChatRailUpdate>()
            val rail =
                rail(this, { clock }, updates, { _, _ ->
                    NetworkResult.Failure(NetworkError.Unknown("boom", RuntimeException("boom")))
                })

            rail.request(force = true)
            advanceUntilIdle()
            val first = updates.filterIsInstance<ChatRailUpdate.Failed>().single()

            rail.request(force = true)
            advanceUntilIdle()
            val second = updates.filterIsInstance<ChatRailUpdate.Failed>().last()

            assertEquals(10_000L, first.backoffMs)
            assertTrue(
                second.backoffMs > first.backoffMs,
                "a second consecutive failure must wait longer than the first",
            )
        }
}
