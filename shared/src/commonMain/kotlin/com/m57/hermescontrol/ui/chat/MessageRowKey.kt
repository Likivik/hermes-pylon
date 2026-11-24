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
 * question. `message_uid` is minted per insert, so the store's byte-identical triples each carry their own uid:
 * they are three distinct rows that must be shown once. Only content plus placement can tell those apart from
 * three genuinely different messages.
 *
 * Content is included on purpose — that is what makes the triples compare equal. It is safe here because this
 * is run over a single ingest, never across a row's lifetime; a tool row that mutates in place is not compared
 * against its own past.
 */
fun messageRowFingerprint(message: ChatMessage): String =
    fnv1a64("${message.role.name}|${message.timestamp}|${message.content}")

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
