package com.m57.hermescontrol.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cache is what makes a session switch instant on web, so its keying and eviction are
 * behaviour, not detail: a wrong key shows one profile's conversation under another, and a wrong
 * bound either grows without limit or evicts what the user is looking at.
 */
class TranscriptCacheTest {
    private fun message(id: String) = ChatMessage(id = id, role = MessageRole.ASSISTANT, content = id)

    @Test
    fun sameSessionIdUnderDifferentProfilesNeverCollides() {
        val cache = TranscriptCache()
        cache.put("default", "session-1", listOf(message("a")))
        cache.put("probe2", "session-1", listOf(message("b")))

        assertEquals(listOf("a"), cache.get("default", "session-1").map { it.content })
        assertEquals(listOf("b"), cache.get("probe2", "session-1").map { it.content })
    }

    @Test
    fun anEmptyTranscriptIsNotStored() {
        val cache = TranscriptCache()
        cache.put("default", "session-1", emptyList())

        assertEquals(0, cache.size)
        assertTrue(cache.get("default", "session-1").isEmpty())
    }

    @Test
    fun theOldestEntryIsEvictedFirst() {
        val cache = TranscriptCache(limit = 2)
        cache.put("default", "s1", listOf(message("one")))
        cache.put("default", "s2", listOf(message("two")))
        cache.put("default", "s3", listOf(message("three")))

        assertEquals(2, cache.size)
        assertTrue("s1 should be evicted", cache.get("default", "s1").isEmpty())
        assertEquals(listOf("two"), cache.get("default", "s2").map { it.content })
        assertEquals(listOf("three"), cache.get("default", "s3").map { it.content })
    }

    @Test
    fun rePuttingTheSameSessionRefreshesItsPosition() {
        val cache = TranscriptCache(limit = 2)
        cache.put("default", "s1", listOf(message("one")))
        cache.put("default", "s2", listOf(message("two")))
        cache.put("default", "s1", listOf(message("one-refreshed")))
        cache.put("default", "s3", listOf(message("three")))

        assertEquals(listOf("one-refreshed"), cache.get("default", "s1").map { it.content })
        assertTrue("s2 should be the evicted one", cache.get("default", "s2").isEmpty())
    }

    @Test
    fun aMissingProfileFallsBackToTheDefaultKey() {
        val cache = TranscriptCache()
        cache.put(null, "session-1", listOf(message("a")))

        assertEquals(listOf("a"), cache.get("default", "session-1").map { it.content })
    }
}
