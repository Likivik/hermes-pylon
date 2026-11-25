package com.m57.hermescontrol.ui.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The arithmetic the transcript makes when an authoritative page lands or a refresh fails.
 *
 * These cases previously existed only as assertions inside `ChatViewModelTest`, reachable by driving
 * coroutines and a fake transport. Here they are one call each, which is what makes them worth having
 * as the state model grows.
 */
class TranscriptReducerTest {
    private fun message(
        id: String,
        content: String = "hi",
        role: MessageRole = MessageRole.USER,
    ) = ChatMessage(id = id, role = role, content = content)

    private fun loaded(
        authoritative: List<ChatMessage>,
        baselineIds: Set<String> = emptySet(),
        cachedIds: Set<String> = emptySet(),
        hasResumeHistory: Boolean = false,
        useLatestPaging: Boolean = true,
        returned: Int = authoritative.size,
        offset: Int = 0,
        pageSize: Int = 150,
    ) = TranscriptIntent.HistoryLoaded(
        authoritative = authoritative,
        baselineIds = baselineIds,
        cachedIds = cachedIds,
        hasResumeHistory = hasResumeHistory,
        useLatestPaging = useLatestPaging,
        returned = returned,
        offset = offset,
        pageSize = pageSize,
    )

    @Test
    fun loadStartedClearsThePreviousError() {
        val before = TranscriptState(errorMessage = "Failed to load messages: boom", isLoading = false)

        val after = TranscriptReducer.reduce(before, TranscriptIntent.LoadStarted)

        assertTrue(after.isLoading)
        assertNull(after.errorMessage)
    }

    @Test
    fun loadedPageReplacesContentAndClearsTheStaleMarker() {
        val before =
            TranscriptState(
                messages = listOf(message("a")),
                isLoading = true,
                staleSince = 1_000L,
                errorMessage = "Failed to load messages: boom",
            )

        // "a" was on screen before the load, so it is in the baseline and the page replaces it.
        val after =
            TranscriptReducer.reduce(before, loaded(listOf(message("b")), baselineIds = setOf("a")))

        assertEquals(listOf("b"), after.messages.map { it.id })
        assertFalse(after.isLoading)
        assertNull(after.staleSince)
    }

    @Test
    fun messagesSentDuringTheLoadSurviveIt() {
        // "mine" arrived after the load started and is not in the baseline, the cache or the page.
        val before = TranscriptState(messages = listOf(message("old"), message("mine")), isLoading = true)

        val after =
            TranscriptReducer.reduce(
                before,
                loaded(
                    authoritative = listOf(message("old"), message("server-new")),
                    baselineIds = setOf("old"),
                ),
            )

        assertEquals(listOf("old", "server-new", "mine"), after.messages.map { it.id })
    }

    @Test
    fun cachedMessagesAreNotKeptAlongsideTheAuthoritativePage() {
        // A cached copy of "cached" would otherwise duplicate it once the page contains the same id.
        val before = TranscriptState(messages = listOf(message("cached")), isLoading = true)

        val after =
            TranscriptReducer.reduce(
                before,
                loaded(authoritative = listOf(message("cached")), cachedIds = setOf("cached")),
            )

        assertEquals(listOf("cached"), after.messages.map { it.id })
    }

    @Test
    fun anEmptyPageDoesNotEraseAResumePlaceholder() {
        val before = TranscriptState(messages = listOf(message("resume-s1-1")))

        val after =
            TranscriptReducer.reduce(
                before,
                loaded(authoritative = emptyList(), hasResumeHistory = true),
            )

        assertEquals(listOf("resume-s1-1"), after.messages.map { it.id })
    }

    @Test
    fun anEmptyPageWithoutResumeHistoryDoesEraseContent() {
        val before = TranscriptState(messages = listOf(message("stale-local")))

        // It is in the baseline: the page does not represent it, so the page replaces it — empty.
        val after =
            TranscriptReducer.reduce(
                before,
                loaded(
                    authoritative = emptyList(),
                    hasResumeHistory = false,
                    baselineIds = setOf("stale-local"),
                ),
            )

        assertTrue(after.messages.isEmpty())
    }

    @Test
    fun aFullLatestPageMeansThereMayBeOlderMessages() {
        val after =
            TranscriptReducer.reduce(
                TranscriptState(),
                loaded(listOf(message("a")), returned = 150, pageSize = 150),
            )

        assertTrue(after.hasOlderMessages)
    }

    @Test
    fun aShortLatestPageMeansThereIsNothingOlder() {
        val after =
            TranscriptReducer.reduce(
                TranscriptState(),
                loaded(listOf(message("a")), returned = 149, pageSize = 150),
            )

        assertFalse(after.hasOlderMessages)
    }

    @Test
    fun aBackwardPageAtOffsetZeroOffersNothingOlder() {
        val after =
            TranscriptReducer.reduce(
                TranscriptState(),
                loaded(
                    listOf(message("a")),
                    useLatestPaging = false,
                    offset = 0,
                ),
            )

        assertFalse(after.hasOlderMessages)
    }

    @Test
    fun failureWithContentKeepsItAndMarksItStale() {
        val before = TranscriptState(messages = listOf(message("a")), isLoading = true)

        val after =
            TranscriptReducer.reduce(
                before,
                TranscriptIntent.HistoryFailed(reason = "offline", nowMs = 5_000L),
            )

        assertEquals(listOf("a"), after.messages.map { it.id })
        assertEquals(5_000L, after.staleSince)
        assertNull(after.errorMessage)
        assertFalse(after.isLoading)
    }

    @Test
    fun repeatedFailureKeepsTheFirstStaleTimestamp() {
        val before =
            TranscriptState(messages = listOf(message("a")), staleSince = 5_000L)

        val after =
            TranscriptReducer.reduce(
                before,
                TranscriptIntent.HistoryFailed(reason = "offline", nowMs = 9_000L),
            )

        assertEquals(5_000L, after.staleSince)
    }

    @Test
    fun failureWithoutContentShowsTheErrorAndNoStaleMarker() {
        val before = TranscriptState(messages = emptyList(), isLoading = true)

        val after =
            TranscriptReducer.reduce(
                before,
                TranscriptIntent.HistoryFailed(reason = "boom", nowMs = 5_000L),
            )

        assertEquals("Failed to load messages: boom", after.errorMessage)
        assertNull(after.staleSince)
        assertFalse(after.isLoading)
    }

    @Test
    fun olderLoadStartedSetsTheOlderFlag() {
        val after = TranscriptReducer.reduce(TranscriptState(), TranscriptIntent.OlderLoadStarted)

        assertTrue(after.isLoadingOlder)
    }

    @Test
    fun anOlderPageIsPrependedToTheTranscript() {
        val before = TranscriptState(messages = listOf(message("new")))

        val after =
            TranscriptReducer.reduce(
                before,
                TranscriptIntent.OlderLoaded(
                    older = listOf(message("old")),
                    useLatestPaging = true,
                    returned = 1,
                    effectiveOffset = 0,
                    limit = 150,
                ),
            )

        assertEquals(listOf("old", "new"), after.messages.map { it.id })
    }

    @Test
    fun aMessageRepeatedAcrossAPageBoundaryAppearsOnce() {
        val before = TranscriptState(messages = listOf(message("shared"), message("new")))

        val after =
            TranscriptReducer.reduce(
                before,
                TranscriptIntent.OlderLoaded(
                    older = listOf(message("old"), message("shared")),
                    useLatestPaging = true,
                    returned = 2,
                    effectiveOffset = 0,
                    limit = 150,
                ),
            )

        assertEquals(listOf("old", "shared", "new"), after.messages.map { it.id })
    }

    @Test
    fun aShortOlderPageEndsThePaging() {
        val after =
            TranscriptReducer.reduce(
                TranscriptState(messages = listOf(message("new"))),
                TranscriptIntent.OlderLoaded(
                    older = listOf(message("old")),
                    useLatestPaging = true,
                    returned = 149,
                    effectiveOffset = 0,
                    limit = 150,
                ),
            )

        assertFalse(after.hasOlderMessages)
    }
}
