package com.m57.hermescontrol.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persisted chat message. Maps to the [com.m57.hermescontrol.ui.chat.ChatMessage]
 * UI model but is stored independently so it survives process death.
 *
 * Messages are scoped by [sessionId] so switching sessions loads the right
 * thread. The [timestamp] field preserves original ordering even if Room
 * reorders internally.
 */
@Entity(
    tableName = "chat_messages",
    indices = [
        Index(value = ["session_id", "timestamp"]),
    ],
)
data class ChatMessageEntity(
    @PrimaryKey
    override val id: String,
    @ColumnInfo(name = "session_id")
    val sessionId: String,
    @ColumnInfo(name = "role")
    override val role: String,
    @ColumnInfo(name = "content")
    override val content: String,
    @ColumnInfo(name = "reasoning_text", defaultValue = "")
    override val reasoningText: String = "",
    @ColumnInfo(name = "timestamp")
    override val timestamp: Long,
    @ColumnInfo(name = "tool_name")
    override val toolName: String? = null,
    @ColumnInfo(name = "tool_call_id")
    override val toolCallId: String? = null,
    @ColumnInfo(name = "tool_status")
    override val toolStatus: String? = null,
    @ColumnInfo(name = "is_streaming")
    override val isStreaming: Boolean = false,
    @ColumnInfo(name = "attachments_json", defaultValue = "[]")
    override val attachmentsJson: String = "[]",
    @ColumnInfo(name = "display_kind")
    override val displayKind: String? = null,
) : ChatMessageRow
