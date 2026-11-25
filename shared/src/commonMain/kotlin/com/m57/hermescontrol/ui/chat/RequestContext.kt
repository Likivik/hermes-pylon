package com.m57.hermescontrol.ui.chat

/*
 * The answer's address.
 *
 * A request goes out for one conversation, over one client, as one account. If any of the three has moved by the
 * time the answer comes back, the answer is addressed to a screen that no longer exists and must be dropped.
 * Every check is one call to [isStaleAgainst] instead of a hand-written comparison per call site.
 */

/**
 * Everything that says which screen an answer is for.
 *
 * The three fields cannot be mixed up: [sessionId] names the conversation, [generation] names the client,
 * [profileId] names the account. One data class means no site can compare two of them and forget the third.
 */
data class RequestContext(
    val sessionId: String?,
    val generation: Long,
    val profileId: String?,
)

/**
 * True when this answer is for a screen that is gone.
 *
 * `null` is a value, not a wildcard: two contexts with a `null` session match each other, because both mean
 * "no conversation yet". A stale answer is one addressed to any of the three that has since changed.
 */
fun RequestContext.isStaleAgainst(current: RequestContext): Boolean =
    sessionId != current.sessionId ||
        generation != current.generation ||
        profileId != current.profileId
