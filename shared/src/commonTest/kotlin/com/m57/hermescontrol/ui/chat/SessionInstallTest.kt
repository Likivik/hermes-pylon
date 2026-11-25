package com.m57.hermescontrol.ui.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The install rules for session create and session branch, including the differences between them
 * that were previously only implicit in two inline `copy` chains.
 */
class SessionInstallTest {
    private fun message(id: String) = ChatMessage(id = id, role = MessageRole.USER, content = "hi")

    private fun busyTranscript() =
        TranscriptState(
            messages = listOf(message("old")),
            currentSessionId = "parent",
            isSessionReady = false,
            isLoading = true,
            chatTitle = "Parent title",
            subagentIndicators = listOf(SubagentIndicator(type = "subagent.start", goal = "worker")),
        )

    @Test
    fun aNewSessionClearsTheTranscriptAndTakesTheStorageId() {
        val after = busyTranscript().withInstalledSession(storageId = "stored-1")

        assertEquals("stored-1", after.currentSessionId)
        assertTrue(after.isSessionReady)
        assertTrue(after.messages.isEmpty())
        assertTrue(after.subagentIndicators.isEmpty())
        assertEquals("Hermes", after.chatTitle)
    }

    @Test
    fun aNewSessionDoesNotTouchTheContextGauge() {
        val before = busyTranscript().copy(contextUsage = ContextUsage(usedTokens = 1_000L, maxTokens = 2_000L))

        val after = before.withInstalledSession(storageId = "stored-1")

        // A create leaves whatever the screen had; only a branch replaces the gauge.
        assertEquals(before.contextUsage, after.contextUsage)
    }

    @Test
    fun aBranchReplacesTheGaugeEvenWhenTheChildReportsNone() {
        val before = busyTranscript().copy(contextUsage = ContextUsage(usedTokens = 1_000L, maxTokens = 2_000L))

        val after =
            before.withInstalledSession(
                storageId = "child",
                contextUsage = null,
                replaceContextUsage = true,
            )

        // Inheriting the parent's gauge into a child is the bug this flag exists to prevent.
        assertNull(after.contextUsage)
    }

    @Test
    fun aBranchKeepsTheReportedTitle() {
        val after = busyTranscript().withInstalledSession(storageId = "child", title = "Child title")

        assertEquals("Child title", after.chatTitle)
    }

    @Test
    fun aNewSessionHasNoModelLabelUntilOneIsReported() {
        val before = ComposerState(currentSessionModel = "openai/gpt-5", terminalBackend = "local")

        val after = before.withNoSessionModel()

        assertNull(after.currentSessionModel)
        assertNull(after.terminalBackend)
    }

    @Test
    fun aBranchTakesItsOwnModelAndTreatsBlankAsNotReported() {
        val before = ComposerState(currentSessionModel = "openai/gpt-5", reasoningLevel = "high")

        val after =
            before.withSessionModel(
                provider = "openai",
                model = "gpt-5-mini",
                reasoningLevel = "",
                terminalBackend = "docker",
            )

        assertEquals("openai/gpt-5-mini", after.currentSessionModel)
        assertNull(after.reasoningLevel, "blank means not reported")
        assertEquals("docker", after.terminalBackend)
    }

    @Test
    fun aBranchWithNoModelReportedClearsTheParentLabel() {
        val before = ComposerState(currentSessionModel = "openai/gpt-5")

        val after =
            before.withSessionModel(
                provider = "openai",
                model = "",
                reasoningLevel = null,
                terminalBackend = null,
            )

        assertNull(after.currentSessionModel)
    }

    @Test
    fun anUnqualifiedModelIsQualifiedByTheBranchRule() {
        val after =
            ComposerState().withSessionModel(
                provider = "anthropic",
                model = "claude",
                reasoningLevel = null,
                terminalBackend = null,
            )

        assertNotNull(after.currentSessionModel)
        assertEquals("anthropic/claude", after.currentSessionModel)
    }

    @Test
    fun aResumeInstallsTheSessionAndStopsLoading() {
        val before = TranscriptState(currentSessionId = "old", isLoading = true, isSessionReady = false)

        val after =
            before.withResumedSession(
                sessionId = "resumed",
                runtimeReady = true,
                contextUsage = null,
            )

        assertEquals("resumed", after.currentSessionId)
        assertTrue(after.isSessionReady)
        assertTrue(!after.isLoading)
        assertNull(after.contextUsage)
    }

    @Test
    fun aResumeTakesTheRuntimeReadyFlagFromTheAck() {
        val after =
            TranscriptState(isSessionReady = true).withResumedSession(
                sessionId = "s",
                runtimeReady = false,
                contextUsage = null,
            )

        // The ack carried no runtime id, so the session is not ready to send into.
        assertTrue(!after.isSessionReady)
    }

    @Test
    fun aResumeQualifiesANewModel() {
        val after =
            ComposerState(currentSessionModel = "anthropic/old").withResumedModel(
                provider = "openai",
                model = "gpt-5",
                reasoningLevel = null,
                terminalBackend = null,
            )

        assertEquals("openai/gpt-5", after.currentSessionModel)
    }

    @Test
    fun aResumeDoesNotDoubleAnAlreadyQualifiedModel() {
        // This branch used to concatenate provider + model unconditionally.
        val after =
            ComposerState().withResumedModel(
                provider = "openai",
                model = "openai/gpt-5",
                reasoningLevel = null,
                terminalBackend = null,
            )

        assertEquals("openai/gpt-5", after.currentSessionModel)
    }

    @Test
    fun aResumeWithNoModelReportedKeepsTheCurrentLabel() {
        val after =
            ComposerState(currentSessionModel = "openai/gpt-5").withResumedModel(
                provider = null,
                model = null,
                reasoningLevel = null,
                terminalBackend = null,
            )

        assertEquals("openai/gpt-5", after.currentSessionModel)
    }

    @Test
    fun aResumeWithBlankReasoningClearsThePreviousEffort() {
        val after =
            ComposerState(reasoningLevel = "high", terminalBackend = "local").withResumedModel(
                provider = null,
                model = null,
                reasoningLevel = "",
                terminalBackend = "  ",
            )

        assertNull(after.reasoningLevel)
        assertNull(after.terminalBackend)
    }
}
