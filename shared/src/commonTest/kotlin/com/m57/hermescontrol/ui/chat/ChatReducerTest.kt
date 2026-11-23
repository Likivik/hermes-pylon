package com.m57.hermescontrol.ui.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins that each intent reaches exactly one rule and no other slice.
 *
 * The rules themselves are covered where they live. What is under test here is the routing: that asking for a
 * status pill does not also touch the composer, and that the transcript is the one slice that keeps its own
 * reducer. This is the property that makes an update atomic — one intent, one new state, no cross-talk.
 */
class ChatReducerTest {
    // ── Connection ───────────────────────────────────────────────────────

    @Test
    fun firstConnectedLatchesAndLeavesTheComposerAlone() {
        val before = ChatUiState()
        val after = ChatReducer.reduce(before, ChatIntent.FirstConnected)
        assertTrue(after.connection.hasConnectedOnce)
        assertEquals(before.composer, after.composer)
        assertEquals(before.transcript, after.transcript)
    }

    @Test
    fun statusPillChangedShowsTheText() {
        val after = ChatReducer.reduce(ChatUiState(), ChatIntent.StatusPillChanged("Loading"))
        assertEquals("Loading", after.connection.statusPill)
    }

    @Test
    fun statusPillClearedClearsOnlyThePill() {
        val latched = ChatReducer.reduce(ChatUiState(), ChatIntent.FirstConnected)
        val after = ChatReducer.reduce(latched, ChatIntent.StatusPillCleared)
        assertNull(after.connection.statusPill)
        assertTrue(after.connection.hasConnectedOnce)
    }

    // ── Composer ─────────────────────────────────────────────────────────

    @Test
    fun slashUsageCountsReachTheComposerOnly() {
        val before = ChatUiState()
        val after = ChatReducer.reduce(before, ChatIntent.SlashUsageCountsChanged(mapOf("/model" to 2)))
        assertEquals(mapOf("/model" to 2), after.composer.slashUsageCounts)
        assertEquals(before.connection, after.connection)
        assertEquals(before.transcript, after.transcript)
    }

    @Test
    fun historySheetRequestedRaisesAndLowers() {
        val on = ChatReducer.reduce(ChatUiState(), ChatIntent.HistorySheetRequested(true))
        assertEquals(true, on.composer.openHistoryRequested)
        val off = ChatReducer.reduce(on, ChatIntent.HistorySheetRequested(false))
        assertEquals(false, off.composer.openHistoryRequested)
    }

    @Test
    fun aBlankPrefillIsStoredAsAbsent() {
        val set = ChatReducer.reduce(ChatUiState(), ChatIntent.PrefillRequested("hello"))
        assertEquals("hello", set.composer.pendingPrefillText)
        val blank = ChatReducer.reduce(set, ChatIntent.PrefillRequested("   "))
        assertNull(blank.composer.pendingPrefillText)
    }

    @Test
    fun profileChangedDropsTheModelAndPrefill() {
        val seeded =
            ChatReducer.reduce(
                ChatReducer.reduce(ChatUiState(), ChatIntent.SessionModelConfirmed("openai/gpt-5")),
                ChatIntent.PrefillRequested("hello"),
            )
        val after = ChatReducer.reduce(seeded, ChatIntent.ProfileChanged)
        assertNull(after.composer.currentSessionModel)
        assertNull(after.composer.pendingPrefillText)
    }

    @Test
    fun theContextLengthAndItsModelAreSetTogether() {
        val after = ChatReducer.reduce(ChatUiState(), ChatIntent.ModelContextMeasured(200_000L, "openai/gpt-5"))
        assertEquals(200_000L, after.composer.modelContextLength)
        assertEquals("openai/gpt-5", after.composer.modelContextLengthModel)
    }

    @Test
    fun typingSettingsAreAppliedAsAPair() {
        val after = ChatReducer.reduce(ChatUiState(), ChatIntent.TypingSettingsLoaded(true, 40))
        assertEquals(true, after.composer.typingEffectEnabled)
        assertEquals(40, after.composer.typingEffectDelayMs)
    }

    @Test
    fun sessionModelConfirmedReplacesTheCurrentOne() {
        val after = ChatReducer.reduce(ChatUiState(), ChatIntent.SessionModelConfirmed("openai/gpt-5"))
        assertEquals("openai/gpt-5", after.composer.currentSessionModel)
    }

    @Test
    fun reasoningLevelChangedIsRecorded() {
        val after = ChatReducer.reduce(ChatUiState(), ChatIntent.ReasoningLevelChanged("high"))
        assertEquals("high", after.composer.reasoningLevel)
    }

    // ── Transcript ───────────────────────────────────────────────────────

    @Test
    fun aTranscriptIntentGoesToTheTranscriptReducer() {
        val before = ChatUiState()
        val after = ChatReducer.reduce(before, ChatIntent.Transcript(TranscriptIntent.LoadStarted))
        assertTrue(after.transcript.isLoading)
        assertEquals(before.composer, after.composer)
        assertEquals(before.connection, after.connection)
    }
}
