package com.m57.hermescontrol.ui.chat

/**
 * Whether an older page joined cleanly onto what is already shown.
 *
 * Offset paging has no guarantee that the window you asked for is the window you get: rows can be inserted
 * or rewritten between two requests, so `offset=150` returns a different 150 rows than it did a moment ago.
 * The response alone cannot tell you — a gap and a clean join look identical.
 *
 * So the page is fetched with **overlap**: a few rows we already hold are re-requested at the seam. If they
 * come back identical, the window is where we thought it was. If they do not, rows were skipped and the caller
 * must re-anchor rather than silently splice two unrelated ranges together.
 *
 * This is what makes loss *detectable*. It does not make it impossible — that needs a server-side cursor,
 * which the route does not offer (see the `after_id` incompatibility tracked in the paging issue).
 */
sealed interface OlderPageStitch {
    /** The seam matched. [older] is only the rows we did not already hold. */
    data class Joined(
        val older: List<ChatMessage>,
    ) : OlderPageStitch

    /** The overlap did not match what we hold: rows were skipped. Do not splice. */
    data object SeamBroken : OlderPageStitch
}

/**
 * Splices an older page onto what is already shown, checking the seam first.
 *
 * [held] and [fetched] are both oldest-first. [fetched] was requested with `limit + overlap` rows, so its last
 * [overlap] rows should be the first [overlap] rows of [held]. On a match those repeats are dropped and the
 * genuinely new rows are returned.
 */
fun stitchOlderPage(
    sessionId: String,
    held: List<ChatMessage>,
    fetched: List<ChatMessage>,
    overlap: Int,
): OlderPageStitch {
    // With nothing on screen there is no seam to verify: this page is the whole load, not a join.
    if (overlap <= 0 || held.isEmpty()) return OlderPageStitch.Joined(fetched)

    // The seam is compared by content hash, never by `id`. `id` is only stable when the payload carried a
    // `message_uid`; its fallbacks are a rowid and a list offset, both of which move — and the standing rule
    // for this store is that a `messages_read` id is not a cross-read identifier, content hash or scope is.
    // A row whose content changed between the two requests fails the check and we re-anchor, which is the
    // safe direction: a false SeamBroken refetches, a false Joined silently hides a lost row.
    val fetchedSeam = fetched.takeLast(overlap).map { messageRowSeamKey(sessionId, it) }
    val heldSeam = held.take(overlap).map { messageRowSeamKey(sessionId, it) }

    // A short page is the one that ends at the very start of the transcript, so the seam it should have
    // repeated is not all present. What did come back must still match the front of what we hold.
    if (fetchedSeam.size < overlap) {
        return if (fetchedSeam == heldSeam.take(fetchedSeam.size)) {
            OlderPageStitch.Joined(fetched.dropLast(fetchedSeam.size))
        } else {
            OlderPageStitch.SeamBroken
        }
    }

    return if (fetchedSeam == heldSeam) {
        OlderPageStitch.Joined(fetched.dropLast(overlap))
    } else {
        OlderPageStitch.SeamBroken
    }
}
