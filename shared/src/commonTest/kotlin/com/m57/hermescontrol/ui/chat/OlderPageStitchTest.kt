package com.m57.hermescontrol.ui.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the one thing offset paging cannot otherwise tell us: whether the page we got back is adjacent to
 * what we already show.
 *
 * The setup in every case is the same shape — `held` is oldest-first, `fetched` is the older page requested
 * with overlap, so its tail repeats `held`'s head.
 */
class OlderPageStitchTest {
    /**
     * The timestamp is pinned. `ChatMessage.timestamp` defaults to `nowMillis()`, so two fixtures built for
     * "the same row" would otherwise carry different timestamps and compare as different rows — the test
     * would pass or fail depending on whether both ran inside one millisecond.
     */
    private fun msg(
        id: String,
        content: String = "body-$id",
        timestamp: Long = 1_000L,
    ) = ChatMessage(id = id, role = MessageRole.USER, content = content, timestamp = timestamp)

    private val held = listOf(msg("m4"), msg("m5"), msg("m6"))

    @Test
    fun aVerifiedSeamDropsTheRepeatedRows() {
        val fetched = listOf(msg("m1"), msg("m2"), msg("m3"), msg("m4"), msg("m5"))
        val out = stitchOlderPage("s-1", held = held, fetched = fetched, overlap = 2)
        assertEquals(
            OlderPageStitch.Joined(listOf(msg("m1"), msg("m2"), msg("m3"))),
            out,
            "the two rows we already hold must not be shown twice",
        )
    }

    @Test
    fun aShiftedWindowIsNotSpliced() {
        // Rows moved above the window, so the page now ends at m3 instead of repeating m4 and m5.
        val fetched = listOf(msg("m1"), msg("m2"), msg("m3"))
        val out = stitchOlderPage("s-1", held = held, fetched = fetched, overlap = 2)
        assertTrue(out is OlderPageStitch.SeamBroken, "silently splicing would hide the lost rows")
    }

    @Test
    fun theSeamSurvivesTheSameRowsComingBackUnderNewIds() {
        // The store rewrites a logical message to a new row, so a re-read hands us fresh ids for rows we
        // already hold. The seam must still verify — this is what the standing rule on cross-read identity
        // is protecting against, and comparing `id` would fail exactly here.
        val heldNow =
            listOf(
                ChatMessage(id = "rest-s-297874", role = MessageRole.USER, content = "hello"),
                ChatMessage(id = "rest-s-297875", role = MessageRole.USER, content = "world"),
            )
        val fetched =
            listOf(
                ChatMessage(id = "rest-s-100", role = MessageRole.USER, content = "older"),
                ChatMessage(id = "rest-s-298338", role = MessageRole.USER, content = "hello"),
                ChatMessage(id = "rest-s-298339", role = MessageRole.USER, content = "world"),
            )
        val out = stitchOlderPage("s-1", held = heldNow, fetched = fetched, overlap = 2)
        assertEquals(
            OlderPageStitch.Joined(listOf(fetched.first())),
            out,
            "same content, new ids: the seam is intact and only the genuinely new row is added",
        )
    }

    @Test
    fun aHoleInTheMiddleOfThePageStillJoins() {
        // Documented limit: the seam is checked, not the interior. A row dropped from the middle of the
        // page leaves the seam intact, so this splices. Only a server-side cursor can close that.
        val fetched = listOf(msg("m1"), msg("m3"), msg("m4"), msg("m5"))
        val out = stitchOlderPage("s-1", held = held, fetched = fetched, overlap = 2)
        assertTrue(out is OlderPageStitch.Joined)
    }

    @Test
    fun aShortPageThatMatchesTheFrontOfHeldStillJoins() {
        // The page ends at the start of the transcript, so it cannot repeat a full overlap.
        val fetched = listOf(msg("m4"), msg("m5"))
        val out = stitchOlderPage("s-1", held = held, fetched = fetched, overlap = 3)
        assertEquals(OlderPageStitch.Joined(emptyList()), out)
    }

    @Test
    fun aShortPageThatDoesNotMatchIsBroken() {
        val fetched = listOf(msg("zz"), msg("m4"))
        val out = stitchOlderPage("s-1", held = held, fetched = fetched, overlap = 3)
        assertTrue(out is OlderPageStitch.SeamBroken)
    }

    @Test
    fun noOverlapMeansNoCheckAndNothingDropped() {
        val fetched = listOf(msg("m1"), msg("m2"))
        val out = stitchOlderPage("s-1", held = held, fetched = fetched, overlap = 0)
        assertEquals(OlderPageStitch.Joined(fetched), out, "overlap zero opts out of the check entirely")
    }

    @Test
    fun theOlderRowsKeepTheirOrder() {
        val fetched = listOf(msg("m1"), msg("m2"), msg("m3"), msg("m4"))
        val out = stitchOlderPage("s-1", held = held, fetched = fetched, overlap = 1)
        assertEquals(listOf("m1", "m2", "m3"), (out as OlderPageStitch.Joined).older.map { it.id })
    }

    @Test
    fun anEmptyPageOnAnEmptySeamJoins() {
        val out = stitchOlderPage("s-1", held = held, fetched = emptyList(), overlap = 2)
        assertTrue(out is OlderPageStitch.Joined)
    }
}
