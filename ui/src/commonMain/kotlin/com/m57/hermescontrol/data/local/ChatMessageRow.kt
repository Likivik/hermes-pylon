package com.m57.hermescontrol.data.local

/**
 * The persisted-column shape of one chat message, without Room.
 *
 * The Android `ChatMessageEntity` implements this; the mapping to the UI model
 * lives in `commonMain` so every target can replay a stored thread. Room stays a
 * storage detail: nothing here knows about tables, DAOs or SQLite.
 */
interface ChatMessageRow {
    val id: String
    val role: String
    val content: String
    val reasoningText: String
    val timestamp: Long
    val toolName: String?
    val toolCallId: String?
    val toolStatus: String?
    val isStreaming: Boolean
    val attachmentsJson: String
    val displayKind: String?
}
