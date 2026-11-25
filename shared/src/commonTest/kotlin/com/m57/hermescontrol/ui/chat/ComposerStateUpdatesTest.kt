package com.m57.hermescontrol.ui.chat

import com.m57.hermescontrol.data.model.Attachment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The composer reducers set one thing and keep the rest.
 *
 * The split matters: the user's draft, attachments and model choice must survive every background
 * refresh, and a blank prefill must not be shown as text.
 */
class ComposerStateUpdatesTest {
    private val attachment = Attachment(uri = "file:///tmp/a.png", name = "a.png", mimeType = "image/png")

    private fun composerWithContent(): ComposerState =
        ComposerState(
            pendingAttachments = listOf(attachment),
            pendingPrefillText = "draft",
            slashUsageCounts = mapOf("/help" to 3),
            openHistoryRequested = true,
            reasoningLevel = "high",
            currentSessionModel = "model-a",
            terminalBackend = "backend-a",
            modelContextLength = 100L,
            modelContextLengthModel = "model-a",
            typingEffectEnabled = false,
            typingEffectDelayMs = 45,
        )

    @Test
    fun theSlashUsageCountsAreReplacedWholesale() {
        val after = composerWithContent().afterSlashUsageCounts(mapOf("/model" to 1))
        assertEquals(mapOf("/model" to 1), after.slashUsageCounts)
    }

    @Test
    fun theSlashUsageCountRefreshKeepsTheUsersWork() {
        val after = composerWithContent().afterSlashUsageCounts(emptyMap())
        assertTrue(after.slashUsageCounts.isEmpty())
        assertEquals("draft", after.pendingPrefillText)
        assertEquals(listOf(attachment), after.pendingAttachments)
        assertEquals("model-a", after.currentSessionModel)
        assertFalse(after.typingEffectEnabled)
    }

    @Test
    fun theHistoryRequestIsRaisedAndLowered() {
        val raised = composerWithContent().afterOpenHistoryRequest(open = true)
        assertTrue(raised.openHistoryRequested)
        val lowered = raised.afterOpenHistoryRequest(open = false)
        assertFalse(lowered.openHistoryRequested)
    }

    @Test
    fun theHistoryRequestDoesNotDisturbTheDraft() {
        val after = composerWithContent().afterOpenHistoryRequest(open = false)
        assertEquals("draft", after.pendingPrefillText)
        assertEquals(listOf(attachment), after.pendingAttachments)
    }

    @Test
    fun aPrefillWithTextIsStored() {
        val after = composerWithContent().afterPendingPrefill("undo me")
        assertEquals("undo me", after.pendingPrefillText)
    }

    @Test
    fun aBlankPrefillIsNotStored() {
        val after = composerWithContent().afterPendingPrefill("   ")
        assertNull(after.pendingPrefillText)
    }

    @Test
    fun aConsumedPrefillIsCleared() {
        val after = composerWithContent().afterPendingPrefill(null)
        assertNull(after.pendingPrefillText)
        assertEquals(listOf(attachment), after.pendingAttachments)
        assertEquals("high", after.reasoningLevel)
    }

    @Test
    fun aProfileChangeDropsTheSessionModelAndPrefill() {
        val after = composerWithContent().afterProfileChange()
        assertNull(after.currentSessionModel)
        assertNull(after.pendingPrefillText)
    }

    @Test
    fun aProfileChangeKeepsTheUsersSettings() {
        val after = composerWithContent().afterProfileChange()
        assertEquals(listOf(attachment), after.pendingAttachments)
        assertEquals(mapOf("/help" to 3), after.slashUsageCounts)
        assertEquals("high", after.reasoningLevel)
        assertEquals("backend-a", after.terminalBackend)
        assertEquals(45, after.typingEffectDelayMs)
    }

    @Test
    fun theContextLengthIsStoredWithItsModel() {
        val after = composerWithContent().afterModelContextLength(length = 4096L, model = "model-b")
        assertEquals(4096L, after.modelContextLength)
        assertEquals("model-b", after.modelContextLengthModel)
    }

    @Test
    fun theTypingSettingsAreAppliedAsAPair() {
        val after = composerWithContent().afterTypingSettings(enabled = true, delayMs = 10)
        assertTrue(after.typingEffectEnabled)
        assertEquals(10, after.typingEffectDelayMs)
        assertEquals("draft", after.pendingPrefillText)
        assertEquals("model-a", after.currentSessionModel)
    }

    @Test
    fun aConfirmedModelReplacesTheCurrentOne() {
        val after = composerWithContent().afterSessionModel("model-c")
        assertEquals("model-c", after.currentSessionModel)
    }

    @Test
    fun anAbsentConfirmationKeepsTheCurrentModel() {
        val after = composerWithContent().afterSessionModel(null)
        assertEquals("model-a", after.currentSessionModel)
    }

    @Test
    fun theReasoningLevelIsReplaced() {
        val after = composerWithContent().afterReasoningLevel("low")
        assertEquals("low", after.reasoningLevel)
        assertEquals("model-a", after.currentSessionModel)
        assertEquals(listOf(attachment), after.pendingAttachments)
    }
}
