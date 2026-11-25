package com.m57.hermescontrol.ui.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `command.dispatch` result classification, including the asymmetry that the old `when` had and did not
 * document: `exec` falls back to `message`, `plugin` does not.
 */
class DispatchOutcomeTest {
    @Test
    fun sendBecomesAPrompt() {
        val outcome = classifyDispatchResult(mapOf("type" to "send", "message" to "do it"))

        assertEquals(DispatchOutcome.SendPrompt("do it"), outcome)
    }

    @Test
    fun skillBecomesAPromptToo() {
        val outcome = classifyDispatchResult(mapOf("type" to "skill", "message" to "run the skill"))

        assertEquals(DispatchOutcome.SendPrompt("run the skill"), outcome)
    }

    @Test
    fun execShowsItsOutput() {
        val outcome = classifyDispatchResult(mapOf("type" to "exec", "output" to "done"))

        assertEquals(DispatchOutcome.ShowOutput("done"), outcome)
    }

    @Test
    fun execFallsBackToTheMessageField() {
        val outcome = classifyDispatchResult(mapOf("type" to "exec", "message" to "from message"))

        assertEquals(DispatchOutcome.ShowOutput("from message"), outcome)
    }

    @Test
    fun pluginDoesNotFallBackToTheMessageField() {
        // The old branch read `output` only; a message-only plugin result showed nothing.
        val outcome = classifyDispatchResult(mapOf("type" to "plugin", "message" to "ignored"))

        assertEquals(DispatchOutcome.ShowOutput(""), outcome)
    }

    @Test
    fun aliasFollowsItsTarget() {
        val outcome = classifyDispatchResult(mapOf("type" to "alias", "target" to "/status"))

        assertEquals(DispatchOutcome.FollowAlias("/status"), outcome)
    }

    @Test
    fun anAliasWithoutATargetIsIgnored() {
        assertNull(classifyDispatchResult(mapOf("type" to "alias")))
    }

    @Test
    fun anUnknownTypeEchoesTheWholeMap() {
        val outcome = classifyDispatchResult(mapOf("type" to "mystery", "x" to 1))

        assertTrue(outcome is DispatchOutcome.ShowOutput)
        assertTrue((outcome as DispatchOutcome.ShowOutput).output.contains("mystery"))
    }

    @Test
    fun aResultWithoutTypeIsIgnored() {
        assertNull(classifyDispatchResult(mapOf("output" to "orphan")))
        assertNull(classifyDispatchResult("not a map"))
    }
}
