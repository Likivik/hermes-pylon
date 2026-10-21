package com.m57.hermescontrol.ui.chat

/**
 * The persistence operations [ChatPersistenceRepository] needs.
 *
 * Deliberately narrow: the repository only ever asks these three questions, and every
 * one of them is expressed in [ChatMessage] rather than the Room entity, so the seam
 * carries no androidx types. The app implements it over `ChatMessageDao`; a console or
 * web target supplies its own.
 */
interface ChatMessageStore {
    fun upsertAll(
        messages: List<ChatMessage>,
        sessionId: String,
    )

    suspend fun messagesForSession(sessionId: String): List<ChatMessage>

    fun replaceMessagesForSession(
        sessionId: String,
        messages: List<ChatMessage>,
    )
}
