package com.m57.hermescontrol.ui.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Two different questions, two different answers.
 *
 * [messageRowKeys] is identity for Compose: it follows [ChatMessage.id], which survives a re-read because it is
 * built from the store's `message_uid` and not from a SQLite rowid. [messageRowFingerprint] is duplicate
 * detection: the store can hand us the same message as three byte-identical rows, each with its own uid, and
 * those must render once.
 */
class MessageRowKeyTest {
    private fun message(
        id: String,
        content: String = "hello",
        timestamp: Long = 1_000L,
        role: MessageRole = MessageRole.USER,
    ) = ChatMessage(id = id, role = role, content = content, timestamp = timestamp)

    // ── keys ───────────────────────────────────────────────────────────

    @Test
    fun theKeyIsTheIdentity() {
        assertEquals(listOf("m-1"), messageRowKeys(listOf(message(id = "m-1"))))
    }

    @Test
    fun aRepeatedIdentityGetsAStableOccurrenceSuffix() {
        val messages = listOf(message(id = "m-1"), message(id = "m-1"), message(id = "m-2"))
        assertEquals(listOf("m-1", "m-1-1", "m-2"), messageRowKeys(messages))
    }

    @Test
    fun theKeysAreDeterministic() {
        val messages = listOf(message(id = "a"), message(id = "a"), message(id = "b"))
        assertEquals(messageRowKeys(messages), messageRowKeys(messages))
    }

    @Test
    fun keysAreUniqueEvenWhenTheSameRowArrivesTwice() {
        val messages = listOf(message(id = "a"), message(id = "a"), message(id = "a"))
        val keys = messageRowKeys(messages)
        assertEquals(keys.size, keys.toSet().size, "Compose requires every key in a list to be unique")
    }

    @Test
    fun theEmptyListHasNoKeys() {
        assertTrue(messageRowKeys(emptyList()).isEmpty())
    }

    // ── duplicate detection ────────────────────────────────────────────

    @Test
    fun byteIdenticalRowsCompareEqual() {
        assertEquals(
            messageRowFingerprint(message(id = "r1", content = "same")),
            messageRowFingerprint(message(id = "r2", content = "same")),
            "the store's byte-identical triples are distinct rows with distinct uids; only content says they are one",
        )
    }

    @Test
    fun differentContentIsNotADuplicate() {
        assertNotEquals(
            messageRowFingerprint(message(id = "a", content = "one")),
            messageRowFingerprint(message(id = "b", content = "two")),
        )
    }

    @Test
    fun aDifferentRoleIsNotADuplicate() {
        assertNotEquals(
            messageRowFingerprint(message(id = "a", role = MessageRole.USER)),
            messageRowFingerprint(message(id = "b", role = MessageRole.ASSISTANT)),
        )
    }

    @Test
    fun aDifferentTimestampIsNotADuplicate() {
        assertNotEquals(
            messageRowFingerprint(message(id = "a", timestamp = 1_000L)),
            messageRowFingerprint(message(id = "b", timestamp = 1_001L)),
        )
    }

    // ── collapse ───────────────────────────────────────────────────────

    @Test
    fun theStoresTriplesCollapseToOneRow() {
        val triple =
            listOf(
                message(id = "297874", content = "same bytes"),
                message(id = "298338", content = "same bytes"),
                message(id = "298339", content = "same bytes"),
            )
        val out = collapseDuplicateRows(triple)
        assertEquals(1, out.size, "one logical message must render once")
        assertEquals("297874", out.single().id, "the first row wins, and its identity is kept")
    }

    @Test
    fun distinctRowsAreAllKept() {
        val out =
            collapseDuplicateRows(
                listOf(
                    message(id = "1", content = "one"),
                    message(id = "2", content = "two"),
                    message(id = "3", content = "three"),
                ),
            )
        assertEquals(3, out.size)
    }

    @Test
    fun theSameTextAtADifferentMomentIsNotADuplicate() {
        val out =
            collapseDuplicateRows(
                listOf(
                    message(id = "1", content = "yes", timestamp = 1_000L),
                    message(id = "2", content = "yes", timestamp = 2_000L),
                ),
            )
        assertEquals(2, out.size, "a person can say the same thing twice")
    }

    @Test
    fun theSameTextFromADifferentSpeakerIsNotADuplicate() {
        val out =
            collapseDuplicateRows(
                listOf(
                    message(id = "1", content = "ok", role = MessageRole.USER),
                    message(id = "2", content = "ok", role = MessageRole.ASSISTANT),
                ),
            )
        assertEquals(2, out.size)
    }

    @Test
    fun theCollapseKeepsTheOriginalOrder() {
        val out =
            collapseDuplicateRows(
                listOf(
                    message(id = "1", content = "a", timestamp = 1L),
                    message(id = "2", content = "b", timestamp = 2L),
                    message(id = "3", content = "a", timestamp = 1L),
                ),
            )
        assertEquals(listOf("1", "2"), out.map { it.id })
    }

    @Test
    fun collapsingAnEmptyListIsEmpty() {
        assertTrue(collapseDuplicateRows(emptyList()).isEmpty())
    }
}
