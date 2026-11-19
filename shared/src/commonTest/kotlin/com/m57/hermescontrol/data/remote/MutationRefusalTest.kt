package com.m57.hermescontrol.data.remote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * The 409 family carries four meanings; the mapping is measured against gateway 0.23.1, not guessed.
 */
class MutationRefusalTest {
    private fun http(
        code: Int,
        message: String,
    ) = NetworkError.Http(code = code, message = message)

    @Test
    fun staleRevisionIsItsOwnKind() {
        val refusal = http(409, "Conflict (HTTP 409): revision_conflict").asMutationRefusal()

        assertIs<MutationRefusal.StaleRevision>(refusal)
    }

    @Test
    fun aMissingOrPartialFenceIsAClientBug() {
        assertEquals(
            MutationRefusal.FenceMissing,
            http(409, "client_upgrade_required: this app is out of sync").asMutationRefusal(),
        )
        assertEquals(
            MutationRefusal.FenceMissing,
            http(409, "mutation_identity_required").asMutationRefusal(),
        )
    }

    @Test
    fun aBusyGuardIsNotAConflict() {
        assertEquals(MutationRefusal.Busy, http(409, "Conflict (HTTP 409): session_busy").asMutationRefusal())
    }

    @Test
    fun anUnnamedProfileOnADestructiveRoute() {
        val detail = "POST /api/sessions/prune requires an explicit profile: this backend serves several profiles"

        assertEquals(MutationRefusal.ProfileRequired, http(400, detail).asMutationRefusal())
    }

    @Test
    fun goneAndUnauthorizedAreDistinct() {
        assertEquals(MutationRefusal.SessionGone, http(404, "Not Found").asMutationRefusal())
        assertEquals(MutationRefusal.Unauthorized, http(401, "Unauthorized").asMutationRefusal())
    }

    @Test
    fun anythingElseKeepsTheGatewayWording() {
        val refusal = http(500, "boom").asMutationRefusal()

        assertEquals(MutationRefusal.Other("boom"), refusal)
    }
}
