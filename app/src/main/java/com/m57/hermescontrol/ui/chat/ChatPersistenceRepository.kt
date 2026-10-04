package com.m57.hermescontrol.ui.chat

import com.m57.hermescontrol.data.local.ChatMessageDao
import com.m57.hermescontrol.data.local.toEntity
import com.m57.hermescontrol.data.local.toUiModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal fun interface OperationRegistrationHook {
    suspend fun afterRegistration(sessionId: String)
}

/**
 * Wraps Room DAO operations for chat message persistence.
 *
 * Extracted from ChatViewModel to separate persistence concerns from
 * UI state management and WebSocket event handling.
 */
open class ChatPersistenceRepository internal constructor(
    private val dao: ChatMessageDao,
    private val operationRegistrationHook: OperationRegistrationHook,
) {
    constructor(dao: ChatMessageDao) : this(dao, OperationRegistrationHook {})

    private class TranscriptRevision(var generation: Long = 0L)

    private val replacementFences = mutableMapOf<String, TranscriptRevision>()
    private val operationTails = mutableMapOf<String, CompletableDeferred<Unit>>()

    private fun replacementFence(sessionId: String): TranscriptRevision =
        synchronized(replacementFences) {
            replacementFences.getOrPut(sessionId) { TranscriptRevision() }
        }

    private suspend fun <T> enqueueSessionOperation(
        sessionId: String,
        action: suspend () -> T,
    ): T {
        val completion = CompletableDeferred<Unit>()
        val predecessor =
            synchronized(operationTails) {
                operationTails.put(sessionId, completion)
            }
        try {
            operationRegistrationHook.afterRegistration(sessionId)
            predecessor?.await()
            return action()
        } finally {
            withContext(NonCancellable) {
                predecessor?.await()
                completion.complete(Unit)
                synchronized(operationTails) {
                    operationTails.remove(sessionId, completion)
                }
            }
        }
    }

    internal fun queuedSessionCount(): Int = synchronized(operationTails) { operationTails.size }

    fun replacementGeneration(sessionId: String): Long =
        replacementFence(sessionId).let { fence -> synchronized(fence) { fence.generation } }

    fun invalidateReplacementWrites(sessionId: String) {
        replacementFence(sessionId).let { fence -> synchronized(fence) { fence.generation++ } }
    }

    suspend fun awaitSessionOperations(sessionId: String) {
        enqueueSessionOperation(sessionId) {}
    }

    /** Persist a single message for the given session. */
    suspend fun persistMessage(
        message: ChatMessage,
        sessionId: String,
        expectedRevision: Long,
    ): Boolean =
        enqueueSessionOperation(sessionId) {
            val fence = replacementFence(sessionId)
            synchronized(fence) {
                if (fence.generation != expectedRevision) return@synchronized false
                dao.upsertAll(listOf(message.toEntity(sessionId)))
                true
            }
        }

    /** Persist multiple messages in one transaction. */
    suspend fun persistMessages(
        messages: List<ChatMessage>,
        sessionId: String,
        expectedRevision: Long,
    ): Boolean =
        enqueueSessionOperation(sessionId) {
            val fence = replacementFence(sessionId)
            synchronized(fence) {
                if (fence.generation != expectedRevision) return@synchronized false
                dao.upsertAll(messages.map { it.toEntity(sessionId) })
                true
            }
        }

    suspend fun persistMessagesIfCurrent(
        messages: List<ChatMessage>,
        sessionId: String,
        expectedGeneration: Long,
        isOwnerCurrent: () -> Boolean = { true },
    ): Boolean =
        enqueueSessionOperation(sessionId) {
            val fence = replacementFence(sessionId)
            synchronized(fence) {
                if (fence.generation != expectedGeneration || !isOwnerCurrent()) return@synchronized false
                dao.upsertAll(messages.map { it.toEntity(sessionId) })
                true
            }
        }

    /** Load cached messages for a session from Room. */
    open suspend fun loadMessages(sessionId: String): List<ChatMessage> =
        dao.getMessagesForSession(sessionId).map { it.toUiModel() }

    suspend fun replaceMessagesIfCurrent(
        messages: List<ChatMessage>,
        sessionId: String,
        expectedGeneration: Long,
    ): Boolean =
        enqueueSessionOperation(sessionId) {
            val fence = replacementFence(sessionId)
            synchronized(fence) {
                if (fence.generation != expectedGeneration) return@synchronized false
                dao.replaceMessagesForSession(sessionId, messages.map { it.toEntity(sessionId) })
                true
            }
        }
}
