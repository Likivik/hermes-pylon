package com.m57.hermescontrol.ui.chat

import com.m57.hermescontrol.data.local.ChatMessageDao
import com.m57.hermescontrol.data.local.toEntity
import com.m57.hermescontrol.data.local.toUiModel

/**
 * The Room implementation of [ChatMessageStore] — the only place that knows chat
 * messages are stored as `ChatMessageEntity` rows.
 *
 * All entity <-> [ChatMessage] conversion stays in `ChatMessageMapper`, which is why
 * that file did not move: it *is* this adapter's guts.
 */
class RoomChatMessageStore(
    private val dao: ChatMessageDao,
) : ChatMessageStore {
    override fun upsertAll(
        messages: List<ChatMessage>,
        sessionId: String,
    ) = dao.upsertAll(messages.map { it.toEntity(sessionId) })

    override suspend fun messagesForSession(sessionId: String): List<ChatMessage> =
        dao.getMessagesForSession(sessionId).map { it.toUiModel() }

    override fun replaceMessagesForSession(
        sessionId: String,
        messages: List<ChatMessage>,
    ) = dao.replaceMessagesForSession(sessionId, messages.map { it.toEntity(sessionId) })
}
