package com.m57.hermescontrol.ui.chat

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RequestContextTest {
    private fun ctx(
        sessionId: String? = "session-a",
        generation: Long = 7L,
        profileId: String? = "profile-a",
    ) = RequestContext(sessionId, generation, profileId)

    @Test
    fun anIdenticalContextIsNotStale() {
        assertFalse(ctx().isStaleAgainst(ctx()))
    }

    @Test
    fun aChangedSessionIdIsStale() {
        assertTrue(ctx(sessionId = "session-a").isStaleAgainst(ctx(sessionId = "session-b")))
    }

    @Test
    fun aMovedGenerationIsStale() {
        assertTrue(ctx(generation = 7L).isStaleAgainst(ctx(generation = 8L)))
    }

    @Test
    fun aChangedProfileIdIsStale() {
        assertTrue(ctx(profileId = "profile-a").isStaleAgainst(ctx(profileId = "profile-b")))
    }

    @Test
    fun twoContextsWithANullSessionIdMatchEachOther() {
        assertFalse(ctx(sessionId = null).isStaleAgainst(ctx(sessionId = null)))
    }

    @Test
    fun aChangedSessionAndGenerationAreStillOneStaleAnswer() {
        assertTrue(
            ctx(sessionId = "session-a", generation = 7L)
                .isStaleAgainst(ctx(sessionId = "session-b", generation = 8L)),
        )
    }

    @Test
    fun aNullProfileMatchesANullProfile() {
        assertFalse(ctx(profileId = null).isStaleAgainst(ctx(profileId = null)))
    }
}
