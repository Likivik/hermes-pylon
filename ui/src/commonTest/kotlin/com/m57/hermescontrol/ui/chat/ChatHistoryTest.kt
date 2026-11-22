package com.m57.hermescontrol.ui.chat

import com.m57.hermescontrol.data.model.SessionMessage
import com.m57.hermescontrol.data.model.SessionMessagePagination
import com.m57.hermescontrol.data.model.SessionMessagesResponse
import com.m57.hermescontrol.data.remote.NetworkError
import com.m57.hermescontrol.data.remote.NetworkResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The "load older" ordering rules, with no socket and no ViewModel.
 *
 * These are the cases that used to be impossible to test: the rules lived inside a coroutine launched from
 * a 72-line method that also captured fences and bumped tokens. Here every external fact is a lambda.
 */
class ChatHistoryTest {
    private fun fence(stillValid: Boolean = true) = HistoryLoadFence("s-1", 7) { stillValid }

    private fun page(
        offset: Int,
        count: Int = 2,
    ) = SessionMessagesResponse(
        messages = (0 until count).map { SessionMessage(id = it, role = "user", content = "c$it") },
        pagination = SessionMessagePagination(offset = offset, returned = count),
    )

    @Test
    fun aCompletedLoadWritesStartedThenLoadedThenFinished() =
        runTest {
            val updates = mutableListOf<ChatHistoryUpdate>()
            val dispatcher = StandardTestDispatcher(testScheduler)
            val history =
                ChatHistory(
                    io = dispatcher,
                    launch = { block -> launch { block() } },
                    fetchPage = { _, _, _, _ -> NetworkResult.Success(page(offset = 150)) },
                    mapMessages = {
                            _,
                            _,
                            _,
                            _,
                        ->
                        listOf(ChatMessage(id = "x", role = MessageRole.USER, content = "x"))
                    },
                    persist = { _, _, _, _ -> true },
                    captureFence = { fence() },
                    write = { updates += it },
                )

            history.loadOlder(
                sessionId = "s-1",
                hasOlderMessages = true,
                isLoadingOlder = false,
                latestPaging = true,
                loadedOffset = 0,
                pageSize = 150,
            )
            advanceUntilIdle()

            assertEquals(ChatHistoryUpdate.OlderStarted, updates.first())
            assertTrue(updates.last() == ChatHistoryUpdate.OlderFinished)
            val loaded = updates.filterIsInstance<ChatHistoryUpdate.OlderLoaded>().single()
            assertEquals(150, loaded.effectiveOffset)
            assertEquals(150, loaded.limit)
        }

    @Test
    fun aStaleFenceWritesNothing() =
        runTest {
            val updates = mutableListOf<ChatHistoryUpdate>()
            val dispatcher = StandardTestDispatcher(testScheduler)
            val history =
                ChatHistory(
                    io = dispatcher,
                    launch = { block -> launch { block() } },
                    fetchPage = { _, _, _, _ -> NetworkResult.Success(page(offset = 150)) },
                    mapMessages = {
                            _,
                            _,
                            _,
                            _,
                        ->
                        listOf(ChatMessage(id = "x", role = MessageRole.USER, content = "x"))
                    },
                    persist = { _, _, _, _ -> true },
                    captureFence = { fence(stillValid = false) },
                    write = { updates += it },
                )

            history.loadOlder(
                "s-1",
                hasOlderMessages = true,
                isLoadingOlder = false,
                latestPaging = true,
                loadedOffset = 0,
                pageSize = 150,
            )
            advanceUntilIdle()

            assertTrue(
                updates.none { it is ChatHistoryUpdate.OlderLoaded },
                "a page whose fence no longer holds must not reach the transcript",
            )
        }

    @Test
    fun theOlderLoadThatLosesItsRaceWritesNothing() =
        runTest {
            val updates = mutableListOf<ChatHistoryUpdate>()
            val dispatcher = StandardTestDispatcher(testScheduler)
            val gate = CompletableDeferred<Unit>()
            var calls = 0
            val history =
                ChatHistory(
                    io = dispatcher,
                    launch = { block -> launch { block() } },
                    fetchPage = { _, _, _, _ ->
                        calls += 1
                        if (calls == 1) gate.await()
                        NetworkResult.Success(page(offset = 150))
                    },
                    mapMessages = {
                            _,
                            _,
                            _,
                            _,
                        ->
                        listOf(ChatMessage(id = "x", role = MessageRole.USER, content = "x"))
                    },
                    persist = { _, _, _, _ -> true },
                    captureFence = { fence() },
                    write = { updates += it },
                )

            history.loadOlder(
                "s-1",
                hasOlderMessages = true,
                isLoadingOlder = false,
                latestPaging = true,
                loadedOffset = 0,
                pageSize = 150,
            )
            history.loadOlder(
                "s-1",
                hasOlderMessages = true,
                isLoadingOlder = false,
                latestPaging = true,
                loadedOffset = 0,
                pageSize = 150,
            )
            advanceUntilIdle()
            gate.complete(Unit)
            advanceUntilIdle()

            val loaded = updates.filterIsInstance<ChatHistoryUpdate.OlderLoaded>()
            assertEquals(1, loaded.size, "only the newest owner may write; the loser is dropped silently")
        }

    @Test
    fun aFailedFetchNeverWritesLoaded() =
        runTest {
            val updates = mutableListOf<ChatHistoryUpdate>()
            val dispatcher = StandardTestDispatcher(testScheduler)
            val history =
                ChatHistory(
                    io = dispatcher,
                    launch = { block -> launch { block() } },
                    fetchPage = {
                            _,
                            _,
                            _,
                            _,
                        ->
                        NetworkResult.Failure(NetworkError.Unknown("boom", RuntimeException("boom")))
                    },
                    mapMessages = { _, _, _, _ -> emptyList() },
                    persist = { _, _, _, _ -> true },
                    captureFence = { fence() },
                    write = { updates += it },
                )

            history.loadOlder(
                "s-1",
                hasOlderMessages = true,
                isLoadingOlder = false,
                latestPaging = true,
                loadedOffset = 0,
                pageSize = 150,
            )
            advanceUntilIdle()

            assertTrue(updates.none { it is ChatHistoryUpdate.OlderLoaded })
            assertTrue(updates.last() == ChatHistoryUpdate.OlderFinished, "the in-flight flag must still be released")
        }

    @Test
    fun aSessionWithNothingOlderNeverCallsTheGateway() =
        runTest {
            var fetches = 0
            val dispatcher = StandardTestDispatcher(testScheduler)
            val history =
                ChatHistory(
                    io = dispatcher,
                    launch = { block -> launch { block() } },
                    fetchPage = { _, _, _, _ ->
                        fetches += 1
                        NetworkResult.Success(page(0))
                    },
                    mapMessages = { _, _, _, _ -> emptyList() },
                    persist = { _, _, _, _ -> true },
                    captureFence = { fence() },
                    write = {},
                )

            history.loadOlder(
                "s-1",
                hasOlderMessages = false,
                isLoadingOlder = false,
                latestPaging = true,
                loadedOffset = 900,
                pageSize = 150,
            )
            advanceUntilIdle()

            assertEquals(0, fetches)
        }

    @Test
    fun absolutePagingAsksWithoutAnOrderAndLatestPagingAsksForLatest() =
        runTest {
            val orders = mutableListOf<String?>()
            val dispatcher = StandardTestDispatcher(testScheduler)
            val history =
                ChatHistory(
                    io = dispatcher,
                    launch = { block -> launch { block() } },
                    fetchPage = { _, _, _, order ->
                        orders += order
                        NetworkResult.Success(page(0))
                    },
                    mapMessages = { _, _, _, _ -> emptyList() },
                    persist = { _, _, _, _ -> true },
                    captureFence = { fence() },
                    write = {},
                )

            history.loadOlder(
                "s-1",
                hasOlderMessages = true,
                isLoadingOlder = false,
                latestPaging = true,
                loadedOffset = 0,
                pageSize = 150,
            )
            advanceUntilIdle()
            history.loadOlder(
                "s-1",
                hasOlderMessages = true,
                isLoadingOlder = false,
                latestPaging = false,
                loadedOffset = 300,
                pageSize = 150,
            )
            advanceUntilIdle()

            assertEquals(listOf("latest", null), orders)
        }
}
