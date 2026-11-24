package com.m57.hermescontrol.ui.chat

/**
 * Where a row came from, and the handle that source gave it.
 *
 * This replaces three string prefix tests on [ChatMessage.id] (`startsWith("rest-…")`,
 * `startsWith("resume-…")`, and parsing the suffix back out to an integer). A name should say what it is;
 * encoding three meanings into one string so they can be decoded later is how the row identity ended up
 * inseparable from the paging maths.
 *
 * The handle is *not* identity. [Rest.serverIndex] may be a list offset, and [Resume.rowId] is a SQLite rowid —
 * both can move between reads. Identity is [ChatMessage.id]; these describe where the row sat in the source's
 * own ordering.
 */
sealed interface MessageOrigin {
    /** Minted by this client: an optimistic send, or a live WebSocket row. */
    data object Local : MessageOrigin

    /** Read from the REST transcript. [serverIndex] is what paging maths needs from it. */
    data class Rest(
        val serverIndex: Int?,
    ) : MessageOrigin

    /** Carried on a session resume ack. [rowId] is the durable row handle that payload offers. */
    data class Resume(
        val rowId: Long?,
    ) : MessageOrigin
}
