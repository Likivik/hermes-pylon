package com.m57.hermescontrol.ui.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The clarify guard, tested once instead of three times.
 *
 * Each case here corresponds to a write that used to be inline in the ViewModel or the event reducer,
 * where the only way to exercise it was to drive a socket and a session.
 */
class OverlaysReducerTest {
    private fun clarify(
        id: String = "c1",
        session: String = "s1",
        profile: String = "p1",
        generation: Int = 1,
    ) = ClarifyUi(
        text = "Pick one",
        options = listOf("a", "b"),
        clarifyId = id,
        sessionId = session,
        sourceProfileId = profile,
        connectionGeneration = generation,
    )

    @Test
    fun replacementAppliesWhenItIsStillTheSameRequest() {
        val expected = clarify()
        val before = OverlaysState(clarifyRequest = expected)
        val replacement = expected.copy(text = "Pick another")

        val after =
            OverlaysReducer.reduce(
                before,
                OverlaysIntent.ClarifyReplaced(expected = expected, replacement = replacement),
            )

        assertEquals("Pick another", after.clarifyRequest?.text)
    }

    @Test
    fun replacementIsIgnoredOnceTheUserHasMovedOn() {
        val expected = clarify(id = "old")
        val newer = clarify(id = "new")
        val before = OverlaysState(clarifyRequest = newer)

        val after =
            OverlaysReducer.reduce(
                before,
                OverlaysIntent.ClarifyReplaced(expected = expected, replacement = null),
            )

        assertEquals("new", after.clarifyRequest?.clarifyId)
    }

    @Test
    fun aNullReplacementClosesTheRequestItWasGuarding() {
        val expected = clarify()
        val before = OverlaysState(clarifyRequest = expected)

        val after =
            OverlaysReducer.reduce(
                before,
                OverlaysIntent.ClarifyReplaced(expected = expected, replacement = null),
            )

        assertNull(after.clarifyRequest)
    }

    @Test
    fun expiryClosesTheRequestWhenIdSessionProfileAndGenerationAllMatch() {
        val before = OverlaysState(clarifyRequest = clarify())

        val after =
            OverlaysReducer.reduce(
                before,
                OverlaysIntent.ClarifyExpired(
                    clarifyId = "c1",
                    sessionId = "s1",
                    sourceProfileId = "p1",
                    connectionGeneration = 1,
                ),
            )

        assertNull(after.clarifyRequest)
    }

    @Test
    fun aLateExpiryFromAnOlderConnectionCannotCloseALivePrompt() {
        // Same prompt id, but the expiry came from the previous connection generation.
        val before = OverlaysState(clarifyRequest = clarify(generation = 2))

        val after =
            OverlaysReducer.reduce(
                before,
                OverlaysIntent.ClarifyExpired(
                    clarifyId = "c1",
                    sessionId = "s1",
                    sourceProfileId = "p1",
                    connectionGeneration = 1,
                ),
            )

        assertEquals(2, after.clarifyRequest?.connectionGeneration)
    }

    @Test
    fun clearingIsUnconditional() {
        val before = OverlaysState(clarifyRequest = clarify())

        val after = OverlaysReducer.reduce(before, OverlaysIntent.ClarifyCleared)

        assertNull(after.clarifyRequest)
    }
}
