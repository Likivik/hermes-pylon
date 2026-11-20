package com.m57.hermescontrol.ui.chat

/**
 * Recently seen transcripts, keyed by profile *and* session.
 *
 * Web has no Room, so without this every session switch is a guaranteed blank while the network
 * answers. Bounded and insertion-ordered: the oldest entry goes first.
 *
 * This is a cache, never a source of truth. The authoritative load still converges the view, and a
 * stale entry is replaced as soon as the gateway answers. Because the key carries the profile, a
 * profile switch cannot read another profile's conversation even for the same session id.
 *
 * Empty transcripts are not stored: an empty list carries no information, and keeping it would let
 * a cache hit pretend a session "loaded" when nothing was ever fetched.
 */
internal class TranscriptCache(
    private val limit: Int = DEFAULT_LIMIT,
) {
    private val entries = LinkedHashMap<String, List<ChatMessage>>()

    val size: Int get() = entries.size

    fun put(
        profile: String?,
        sessionId: String,
        messages: List<ChatMessage>,
    ) {
        if (messages.isEmpty()) return
        val key = key(profile, sessionId)
        entries.remove(key)
        entries[key] = messages
        while (entries.size > limit) {
            val oldest = entries.keys.firstOrNull() ?: break
            entries.remove(oldest)
        }
    }

    fun get(
        profile: String?,
        sessionId: String,
    ): List<ChatMessage> = entries[key(profile, sessionId)].orEmpty()

    fun clear() = entries.clear()

    private fun key(
        profile: String?,
        sessionId: String,
    ): String = (profile ?: DEFAULT_PROFILE) + "|" + sessionId

    companion object {
        /** How many recent transcripts stay in memory for instant switches. */
        const val DEFAULT_LIMIT = 8

        private const val DEFAULT_PROFILE = "default"
    }
}
