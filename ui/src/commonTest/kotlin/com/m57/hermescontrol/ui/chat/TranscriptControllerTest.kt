package com.m57.hermescontrol.ui.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins what each history event does to the transcript.
 *
 * The controller is the state-write half of the history split: `ChatHistory` produces the events, this writes
 * them. A test builds one controller over plain lambdas, feeds it an event, and reads back what was written.
 */
class TranscriptControllerTest {
    /** Collects the transitions the controller asks for, so a test can assert on them after the fact. */
    private class Harness {
        val transitions = mutableListOf<(ChatUiState) -> ChatUiState>()
        val messageOffsets = mutableListOf<Int>()
        val pagingShapes = mutableListOf<Boolean>()
        var retireCalls = 0

        val controller =
            TranscriptController(
                updateState = { f -> transitions += f },
                setMessageOffset = { messageOffsets += it },
                setLatestPaging = { pagingShapes += it },
                activeCacheMessageIds = { emptySet() },
                readMessages = { emptyList() },
                retireCacheLoad = { retireCalls += 1 },
                restoredTodos = { current, messages -> messages.mapNotNull { null }.ifEmpty { current } },
                nowMs = { 1_000L },
                pageSize = 150,
            )

        fun applied(): List<ChatUiState> = transitions.map { it(ChatUiState()) }
    }

    @Test
    fun olderStartedTurnsOnTheOlderSpinner() {
        val h = Harness()
        h.controller.apply(ChatHistoryUpdate.OlderStarted)
        assertTrue(h.applied().single().transcript.isLoadingOlder)
    }

    @Test
    fun olderFinishedTurnsOffTheOlderSpinner() {
        val h = Harness()
        h.controller.apply(ChatHistoryUpdate.OlderFinished)
        assertEquals(false, h.applied().single().transcript.isLoadingOlder)
    }

    @Test
    fun loadStartedTurnsOnTheLoadSpinner() {
        val h = Harness()
        h.controller.apply(ChatHistoryUpdate.LoadStarted)
        assertTrue(h.applied().single().transcript.isLoading)
    }

    @Test
    fun loadFinishedSyncStartedAndSyncFinishedChangeNothing() {
        val h = Harness()
        h.controller.apply(ChatHistoryUpdate.LoadFinished)
        h.controller.apply(ChatHistoryUpdate.SyncStarted)
        h.controller.apply(ChatHistoryUpdate.SyncFinished)
        assertEquals(0, h.transitions.size)
    }

    @Test
    fun messageCountKnownIsNotTheTranscriptsJob() {
        val h = Harness()
        h.controller.apply(ChatHistoryUpdate.MessageCountKnown(sessionId = "s", count = 3))
        assertEquals(0, h.transitions.size)
    }
}
