package com.m57.hermescontrol.ui.chat

/**
 * Compose item keys for the transcript.
 *
 * [ChatMessage.id] is the identity — see [MessageOrigin] for why a source handle is not. The occurrence suffix
 * exists only because Compose requires every key in a list to be unique, and a source can hand us the same row
 * twice.
 */
fun messageRowKeys(messages: List<ChatMessage>): List<String> {
    val seen = mutableMapOf<String, Int>()
    return messages.map { message ->
        val occurrence = seen[message.id] ?: 0
        seen[message.id] = occurrence + 1
        if (occurrence == 0) message.id else "${message.id}-$occurrence"
    }
}

/**
 * Whether two rows are the same message written twice.
 *
 * This is **not** identity, and it is deliberately not what [messageRowKeys] uses. It answers a different
 * question. `message_uid` is minted per insert, so the store's byte-identical repeats each carry their own uid:
 * they are distinct rows that must be shown once. Only content plus placement can tell those apart from
 * genuinely different messages.
 *
 * The parts are `session_id | role | content | timestamp`, which is the shape the upstream project proposed for
 * a durable message id (`sha256(session_id + role + content + timestamp)`, truncated). We hash the same inputs
 * because they are the right ones — not the same function, since this is identity and not a security boundary.
 *
 * `session_id` is the scope. The upstream doctrine is "cite content hash **or scope**": without the scope the
 * hash alone is only unique inside one session, and nothing here enforces that a caller keeps batches apart.
 *
 * Content is included on purpose — that is what makes the repeats compare equal. It is safe here because this
 * is run over a single ingest, never across a row's lifetime; a tool row that mutates in place is not compared
 * against its own past.
 */
fun messageRowFingerprint(
    sessionId: String,
    message: ChatMessage,
): String = messageRowFingerprint(sessionId, message.role, message.timestamp, message.content)

/**
 * The same fingerprint from its parts, for a caller that is still building the row — see
 * [messageRowFingerprint] for what it is and is not for.
 */
fun messageRowFingerprint(
    sessionId: String,
    role: MessageRole,
    timestamp: Long,
    content: String,
): String = fnv1a64("$sessionId|${role.name}|$content|$timestamp")

/**
 * How two rows from **different reads** are recognised as the same message.
 *
 * Deliberately not [messageRowFingerprint], and deliberately not [ChatMessage.id]. Both can carry a timestamp,
 * and ours is not stable across reads: when the wire omits one the mapper synthesises `nowMillis` per call, so
 * the same row hashes differently each time it is fetched. A seam built on that can never join.
 *
 * So the key keeps only what survives a re-read: the scope and the content.
 */
fun messageRowSeamKey(
    sessionId: String,
    message: ChatMessage,
): String = fnv1a64("$sessionId|${message.role.name}|${message.content}")

/**
 * Collapses the store's byte-identical repeats down to one row.
 *
 * `messages_read` can return one logical message as several rows — measured upstream: one message read twice
 * came back as three byte-identical rows under three different ids. They are distinct rows in the store, so no
 * identity can tell us they are one; only identical content can.
 *
 * The trade-off, stated plainly: two genuinely different messages that share session, role, timestamp *and*
 * content would also collapse. Timestamp is stored at fine enough resolution that this is not a real risk for
 * human input, and a pair of tool rows identical to the byte is not worth showing twice either.
 *
 * Run over one ingest batch, never across a row's lifetime — see [messageRowFingerprint].
 */
fun collapseDuplicateRows(
    sessionId: String,
    messages: List<ChatMessage>,
): List<ChatMessage> {
    val seen = mutableSetOf<String>()
    return messages.filter { seen.add(messageRowFingerprint(sessionId, it)) }
}

/**
 * Collapses the store's byte-identical repeats down to one row.
 *
 * `messages_read` can return one logical message as several rows — measured upstream: one message read twice
 * came back as three byte-identical rows under three different ids. They are distinct rows in the store, so no
 * identity can tell us they are one; only identical content can.
 *
 * The trade-off, stated plainly: two genuinely different messages that share role, timestamp *and* content
 * would also collapse. Timestamp is stored at fine enough resolution that this is not a real risk for human
 * input, and a pair of tool rows identical to the byte is not worth showing twice either.
 *
 * Run over one ingest batch, never across a row's lifetime — see [messageRowFingerprint].
 */
fun collapseDuplicateRows(messages: List<ChatMessage>): List<ChatMessage> {
    val seen = mutableSetOf<String>()
    return messages.filter { seen.add(messageRowFingerprint(it)) }
}

/**
 * FNV-1a, 64-bit. Deliberately not `String.hashCode()`: that is 32-bit and its result is not what a reader
 * expects from a "hash", so a collision reads like a bug in this file rather than a property of the function.
 * The constants are the published FNV-1a 64-bit ones.
 */
private fun fnv1a64(input: String): String {
    var hash = -0x340d631b7bdddcdbL // 0xcbf29ce484222325
    for (ch in input) {
        hash = hash xor (ch.code.toLong() and 0xff)
        hash *= 0x100000001b3L
    }
    return hash.toULong().toString(16)
}
