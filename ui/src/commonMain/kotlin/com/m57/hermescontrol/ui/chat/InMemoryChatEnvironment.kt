package com.m57.hermescontrol.ui.chat

/**
 * Chat history in a map.
 *
 * Correct for a target with no local database: the gateway is the source of
 * truth for history, and this store only backs the "show something before the
 * socket answers" path. Nothing here touches a platform API, so desktop and web
 * share it.
 */
class InMemoryChatMessageStore : ChatMessageStore {
    private val bySession = mutableMapOf<String, List<ChatMessage>>()

    override fun upsertAll(
        messages: List<ChatMessage>,
        sessionId: String,
    ) {
        val existing = bySession[sessionId].orEmpty().toMutableList()
        val indexById = existing.withIndex().associate { (index, message) -> message.id to index }
        messages.forEach { message ->
            val at = indexById[message.id]
            if (at == null) existing += message else existing[at] = message
        }
        bySession[sessionId] = existing
    }

    override suspend fun messagesForSession(sessionId: String): List<ChatMessage> = bySession[sessionId].orEmpty()

    override fun replaceMessagesForSession(
        sessionId: String,
        messages: List<ChatMessage>,
    ) {
        bySession[sessionId] = messages
    }
}

/**
 * Attachments on a target that cannot open them yet.
 *
 * Returns a failure the UI already renders instead of throwing, so chat stays
 * usable for text. Real openers arrive with the media phase.
 */
val unsupportedAttachmentOpener: AttachmentOpener =
    AttachmentOpener { attachment, _ ->
        AttachmentOpenOutcome.Failed(AttachmentOpenError.OPEN, attachment.name)
    }
