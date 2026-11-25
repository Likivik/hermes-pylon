package com.m57.hermescontrol.ui.chat

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * When a sync may skip writing the transcript.
 *
 * The case that matters is a tool row flipping from RUNNING to COMPLETED with the same content: treating that
 * as "same" leaves a finished tool spinning forever.
 */
class SameTranscriptTest {
    private fun message(
        id: String = "m1",
        content: String = "hello",
        toolStatus: ToolStatus? = null,
    ) = ChatMessage(id = id, role = MessageRole.ASSISTANT, content = content, toolStatus = toolStatus)

    @Test
    fun identicalTranscriptsAreTheSame() {
        assertTrue(sameTranscript(listOf(message()), listOf(message())))
    }

    @Test
    fun aChangedToolStatusIsADifferentTranscript() {
        val running = message(toolStatus = ToolStatus.RUNNING)
        val done = message(toolStatus = ToolStatus.COMPLETED)

        assertFalse(
            sameTranscript(listOf(running), listOf(done)),
            "skipping this write would leave a finished tool spinning",
        )
    }

    @Test
    fun aChangedBodyIsADifferentTranscript() {
        assertFalse(sameTranscript(listOf(message(content = "a")), listOf(message(content = "b"))))
    }

    @Test
    fun aDifferentOrderIsADifferentTranscript() {
        val first = message(id = "a")
        val second = message(id = "b")

        assertFalse(sameTranscript(listOf(first, second), listOf(second, first)))
    }

    @Test
    fun aShorterTranscriptIsADifferentTranscript() {
        assertFalse(sameTranscript(listOf(message(), message(id = "m2")), listOf(message())))
    }

    @Test
    fun emptyTranscriptsAreTheSame() {
        assertTrue(sameTranscript(emptyList(), emptyList()))
    }
}
