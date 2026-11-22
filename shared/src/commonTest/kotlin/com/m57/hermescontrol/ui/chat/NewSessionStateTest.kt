package com.m57.hermescontrol.ui.chat

import com.m57.hermescontrol.data.model.Attachment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * "New session" resets the old conversation but keeps the user's in-progress work.
 *
 * The split matters: losing a draft on a new session is a bug, leaking an old transcript is a bug.
 */
class NewSessionStateTest {
    private val attachment = Attachment(uri = "file:///tmp/a.png", name = "a.png", mimeType = "image/png")

    private fun stateWithContent(): ChatUiState =
        ChatUiState(
            transcript =
                TranscriptState(
                    messages = listOf(ChatMessage(role = MessageRole.USER, content = "hi")),
                    currentSessionId = "session-1",
                    isSessionReady = true,
                    chatTitle = "Old chat",
                    isLoading = true,
                    subagentIndicators = listOf(SubagentIndicator(type = "subagent.start")),
                    contextUsage = ContextUsage(usedTokens = 10L, maxTokens = 100L),
                    todos = listOf(TodoItem(id = "1", content = "step")),
                ),
            composer =
                ComposerState(
                    pendingAttachments = listOf(attachment),
                    pendingPrefillText = "draft",
                    slashUsageCounts = mapOf("/help" to 3),
                    reasoningLevel = "high",
                    currentSessionModel = "model-a",
                    terminalBackend = "backend-a",
                    typingEffectEnabled = false,
                ),
            overlays =
                OverlaysState(
                    isSearchActive = true,
                    searchQuery = "needle",
                    showContextDetail = true,
                ),
        )

    @Test
    fun theTranscriptIsWiped() {
        val after = stateWithContent().afterNewSession(setLoading = false)
        assertTrue(after.messages.isEmpty())
        assertTrue(after.todos.isEmpty())
        assertTrue(after.subagentIndicators.isEmpty())
        assertNull(after.currentSessionId)
        assertFalse(after.isSessionReady)
    }

    @Test
    fun theTitleAndContextUsageAreReset() {
        val after = stateWithContent().afterNewSession(setLoading = false)
        assertEquals("Hermes", after.chatTitle)
        assertNull(after.contextUsage)
    }

    @Test
    fun loadingIsSetWhenAsked() {
        assertTrue(stateWithContent().afterNewSession(setLoading = true).isLoading)
    }

    @Test
    fun loadingIsClearedWhenNotAsked() {
        assertFalse(stateWithContent().afterNewSession(setLoading = false).isLoading)
    }

    @Test
    fun theSessionModelAndBackendAreCleared() {
        val after = stateWithContent().afterNewSession(setLoading = false)
        assertNull(after.composer.currentSessionModel)
        assertNull(after.composer.terminalBackend)
        assertNull(after.composer.pendingPrefillText)
    }

    @Test
    fun theUsersWorkIsKept() {
        val after = stateWithContent().afterNewSession(setLoading = false)
        assertEquals(listOf(attachment), after.composer.pendingAttachments)
        assertEquals("high", after.composer.reasoningLevel)
        assertFalse(after.composer.typingEffectEnabled)
        assertEquals(mapOf("/help" to 3), after.composer.slashUsageCounts)
    }

    @Test
    fun theContextDetailClosesAndOtherOverlaysStay() {
        val after = stateWithContent().afterNewSession(setLoading = false)
        assertFalse(after.overlays.showContextDetail)
        assertTrue(after.overlays.isSearchActive)
        assertEquals("needle", after.overlays.searchQuery)
    }
}
