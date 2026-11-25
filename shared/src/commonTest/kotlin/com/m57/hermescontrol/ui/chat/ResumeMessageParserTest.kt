package com.m57.hermescontrol.ui.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Resume-payload parsing, tested as data.
 *
 * Every case here used to be reachable only through a live socket and a resume ack: the rules were
 * inline in a 127-line ViewModel method. They are now a function of (session id, payload, clock).
 */
class ResumeMessageParserTest {
    private fun parse(vararg items: Any?) = parseResumeMessages("s-1", items.toList(), nowMillis = 1_000L)

    @Test
    fun rolesMapToTheTranscriptRoles() {
        val messages =
            parse(
                mapOf("role" to "user", "text" to "hi"),
                mapOf("role" to "assistant", "text" to "hello"),
                mapOf("role" to "system", "text" to "note"),
                mapOf("role" to "tool", "text" to "result"),
            )

        assertEquals(
            listOf(
                MessageRole.USER,
                MessageRole.ASSISTANT,
                MessageRole.SYSTEM,
                MessageRole.TOOL,
            ),
            messages.map {
                it.role
            },
        )
    }

    @Test
    fun roleNamesAreCaseInsensitive() {
        val messages = parse(mapOf("role" to "USER", "text" to "hi"))

        assertEquals(MessageRole.USER, messages.single().role)
    }

    @Test
    fun anUnknownRoleIsDropped() {
        val messages = parse(mapOf("role" to "wizard", "text" to "hi"))

        assertTrue(messages.isEmpty())
    }

    @Test
    fun aMessageWithNoUsableContentIsDropped() {
        val messages =
            parse(
                mapOf("role" to "user", "text" to "   "),
                mapOf("role" to "assistant"),
            )

        assertTrue(messages.isEmpty(), "blank bodies must not become empty bubbles")
    }

    @Test
    fun textPrefersTheTextFieldThenContent() {
        val fromText = parse(mapOf("role" to "user", "text" to "from text", "content" to "from content"))
        val fromContent = parse(mapOf("role" to "user", "content" to "from content"))

        assertEquals("from text", fromText.single().content.orEmpty())
        assertEquals("from content", fromContent.single().content.orEmpty())
    }

    @Test
    fun aToolMessageFallsBackToItsContext() {
        val messages = parse(mapOf("role" to "tool", "context" to "tool context"))

        assertEquals("tool context", messages.single().content.orEmpty())
        assertEquals(ToolStatus.COMPLETED, messages.single().toolStatus)
    }

    @Test
    fun aSecondsTimestampBecomesMillis() {
        val messages = parse(mapOf("role" to "user", "text" to "hi", "timestamp" to 1_700_000_000.0))

        assertEquals(1_700_000_000_000L, messages.single().timestamp)
    }

    @Test
    fun aMissingTimestampFallsBackToTheClockPlusIndex() {
        val messages =
            parse(
                mapOf("role" to "user", "text" to "a"),
                mapOf("role" to "user", "text" to "b"),
            )

        // nowMillis = 1000, index added so ordering survives equal timestamps
        assertEquals(1_000L, messages[0].timestamp)
        assertEquals(1_001L, messages[1].timestamp)
    }

    @Test
    fun idsAreScopedToTheSessionAndIndex() {
        val messages = parse(mapOf("role" to "user", "text" to "hi"))

        assertEquals("resume-s-1-0", messages.single().id)
    }

    @Test
    fun aNonToolMessageHasNoToolName() {
        val messages = parse(mapOf("role" to "user", "text" to "hi", "name" to "someone"))

        assertNull(messages.single().toolName)
    }
}
