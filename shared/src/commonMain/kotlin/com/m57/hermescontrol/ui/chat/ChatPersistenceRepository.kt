package com.m57.hermescontrol.ui.chat

import com.m57.hermescontrol.concurrent.removeIfValue
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

fun interface OperationRegistrationHook {
    suspend fun afterRegistration(sessionId: String)
}

/**
 * Wraps chat message persistence.
 *
 * Extracted from ChatViewModel to separate persistence concerns from UI state
 * management and WebSocket event handling.
 *
 * Storage reaches this through [ChatMessageStore], so the repository itself has no
 * idea whether messages land in Room, a file or memory.
 */
open class ChatPersistenceRepository(
    private val store: ChatMessageStore,
    private val operationRegistrationHook: OperationRegistrationHook,
) {
    constructor(store: ChatMessageStore) : this(store, OperationRegistrationHook {})

    private class TranscriptRevision(var generation: Long = 0L) {
        val lock = SynchronizedObject()
    }

    private val fencesLock = SynchronizedObject()
    private val tailsLock = SynchronizedObject()
    private val replacementFences = mutableMapOf<String, TranscriptRevision>()
    private val operationTails = mutableMapOf<String, CompletableDeferred<Unit>>()

    private fun replacementFence(sessionId: String): TranscriptRevision =
        synchronized(fencesLock) {
            replacementFences.getOrPut(sessionId) { TranscriptRevision() }
        }

    private suspend fun <T> enqueueSessionOperation(
        sessionId: String,
        action: suspend () -> T,
    ): T {
        val completion = CompletableDeferred<Unit>()
        val predecessor =
            synchronized(tailsLock) {
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
                synchronized(tailsLock) {
                    operationTails.removeIfValue(sessionId, completion)
                }
            }
        }
    }

    fun queuedSessionCount(): Int = synchronized(tailsLock) { operationTails.size }

    fun replacementGeneration(sessionId: String): Long =
        replacementFence(sessionId).let { fence -> synchronized(fence.lock) { fence.generation } }

    fun invalidateReplacementWrites(sessionId: String) {
        replacementFence(sessionId).let { fence -> synchronized(fence.lock) { fence.generation++ } }
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
            synchronized(fence.lock) {
                if (fence.generation != expectedRevision) return@synchronized false
                store.upsertAll(listOf(message), sessionId)
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
            synchronized(fence.lock) {
                if (fence.generation != expectedRevision) return@synchronized false
                store.upsertAll(messages, sessionId)
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
            synchronized(fence.lock) {
                if (fence.generation != expectedGeneration || !isOwnerCurrent()) return@synchronized false
                store.upsertAll(messages, sessionId)
                true
            }
        }

    /** Load cached messages for a session. */
    open suspend fun loadMessages(sessionId: String): List<ChatMessage> = store.messagesForSession(sessionId)

    suspend fun replaceMessagesIfCurrent(
        messages: List<ChatMessage>,
        sessionId: String,
        expectedGeneration: Long,
    ): Boolean =
        enqueueSessionOperation(sessionId) {
            val fence = replacementFence(sessionId)
            synchronized(fence.lock) {
                if (fence.generation != expectedGeneration) return@synchronized false
                store.replaceMessagesForSession(sessionId, messages)
                true
            }
        }
}
