package com.m57.hermescontrol.data.remote

import com.m57.hermescontrol.util.HermesLog

/**
 * What a refused session mutation means.
 *
 * Decided once, at the transport seam, from the status and the gateway's structured `detail`.
 * Nothing above this layer parses an error string: the 409 family carries several different
 * meanings and they are not interchangeable.
 *
 * Measured against gateway 0.23.1:
 * - `client_upgrade_required` — the request carried no fence at all (a pre-fence client)
 * - `mutation_identity_required` — a partial fence
 * - `revision_conflict` — the row changed since it was read; nothing was written
 * - `session_busy` — a mutation guard refused the write
 * - 400 with an "explicit profile" detail — a destructive route was given no profile on a
 *   multi-profile host
 */
sealed interface MutationRefusal {
    /** The session changed since the client read it. Reload and retry. */
    data object StaleRevision : MutationRefusal

    /** The client sent no fence, or a partial one. This is a client bug, not a user error. */
    data object FenceMissing : MutationRefusal

    /** Another writer holds the session. */
    data object Busy : MutationRefusal

    /** The route needs an explicit profile and did not get one. */
    data object ProfileRequired : MutationRefusal

    /** The session no longer exists. */
    data object SessionGone : MutationRefusal

    /** Authentication expired. */
    data object Unauthorized : MutationRefusal

    /** Anything else, with the gateway's own words. */
    data class Other(val message: String) : MutationRefusal
}

/** A fence the gateway had to refuse is a client bug: noisy in debug, quiet in release. */
private fun fenceMissing(detail: String): MutationRefusal {
    if (HermesLog.isDebug) HermesLog.e("MutationRefusal", "fence missing — client bug, not a user error: $detail")
    return MutationRefusal.FenceMissing
}

/** Classify a transport failure for a session mutation. Pure, so it is testable without a server. */
fun NetworkError.asMutationRefusal(): MutationRefusal {
    val http = this as? NetworkError.Http ?: return MutationRefusal.Other(message)
    val detail = message
    return when {
        http.code == 401 -> MutationRefusal.Unauthorized
        http.code == 404 -> MutationRefusal.SessionGone
        http.code == 409 && "revision_conflict" in detail -> MutationRefusal.StaleRevision
        http.code == 409 && "mutation_identity_required" in detail -> fenceMissing(detail)
        http.code == 409 && "client_upgrade_required" in detail -> fenceMissing(detail)
        http.code == 409 && "session_busy" in detail -> MutationRefusal.Busy
        http.code == 400 && "explicit profile" in detail -> MutationRefusal.ProfileRequired
        else -> MutationRefusal.Other(detail)
    }
}
