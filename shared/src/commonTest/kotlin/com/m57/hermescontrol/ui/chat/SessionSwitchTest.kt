package com.m57.hermescontrol.ui.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What a session switch resets, and what it must leave alone.
 *
 * Each reset is a bug that used to be visible only by switching sessions in the app: a typing gauge that
 * followed the user, todos from the previous conversation, a model choice leaking across sessions.
 */
class SessionSwitchTest {
    private fun message(id: String) = ChatMessage(id = id, role = MessageRole.USER, content = id)

    private fun busyState() =
        ChatUiState().copy(
            transcript =
                TranscriptState(
                    currentSessionId = "old",
                    messages = listOf(message("old-1")),
                    chatTitle = "Old",
                    isAgentTyping = true,
                    todos = listOf(TodoItem(id = "t1", content = "old todo", status = "pending")),
                    hasOlderMessages = true,
                    isLoadingOlder = true,
                    isSessionReady = true,
                ),
            rail = RailState(showSessionPicker = true),
            composer =
                ComposerState(
                    currentSessionModel = "old/model",
                    terminalBackend = "tmux",
                    pendingPrefillText = "draft",
                ),
            overlays = OverlaysState(showContextDetail = true),
        )

    @Test
    fun theNewSessionAndItsTitleAreInstalled() {
        val next = switchedState(busyState(), "new", listOf(message("cached")), "New")

        assertEquals("new", next.currentSessionId)
        assertEquals("New", next.chatTitle)
        assertEquals(
            listOf("cached"),
            next.messages.map { it.id },
            "the cache paints immediately, so a switch is not a flash",
        )
    }

    @Test
    fun occupancyAndConversationScopedStateDoNotFollowTheUser() {
        val next = switchedState(busyState(), "new", emptyList(), "New")

        assertTrue(!next.isAgentTyping, "the typing gauge is per-session; carrying it over misreports occupancy")
        assertTrue(next.todos.isEmpty())
        assertNull(next.contextUsage)
        assertTrue(next.subagentIndicators.isEmpty())
    }

    @Test
    fun perSessionComposerChoicesAreCleared() {
        val next = switchedState(busyState(), "new", emptyList(), "New")

        assertNull(next.currentSessionModel, "a model choice belongs to the session it was made in")
        assertNull(next.terminalBackend)
        assertNull(next.pendingPrefillText)
    }

    @Test
    fun modalStateAboutTheOldSessionIsClosed() {
        val next = switchedState(busyState(), "new", emptyList(), "New")

        assertTrue(!next.showSessionPicker)
        assertNull(next.overlays.modelSwitchConfirmation)
        assertTrue(!next.overlays.showContextDetail)
    }

    @Test
    fun pagingFlagsAreResetButReadinessIsNot() {
        val next = switchedState(busyState(), "new", emptyList(), "New")

        assertTrue(!next.hasOlderMessages)
        assertTrue(!next.isLoadingOlder)
        assertTrue(!next.isSessionReady)
    }

    @Test
    fun loadingIsLeftExactlyAsItWas() {
        val loading = busyState().copy(transcript = busyState().transcript.copy(isLoading = true))
        val next = switchedState(loading, "new", emptyList(), "New")

        assertTrue(
            next.isLoading,
            "clearing this here raced the fetch completion and left freshly switched sessions stuck on an empty chat",
        )
    }
}
