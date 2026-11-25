package com.m57.hermescontrol.data.local

import com.m57.hermescontrol.data.remote.HermesJson
import com.m57.hermescontrol.ui.chat.ChatMessage
import kotlinx.serialization.encodeToString

/**
 * The Room-specific half of the chat-message mapping.
 *
 * Row → UI lives in `commonMain` ([ChatMessageRow.toUiModel]); this direction has
 * to name the `@Entity` type, so it stays on Android.
 */
fun ChatMessage.toEntity(sessionId: String): ChatMessageEntity =
    ChatMessageEntity(
        id = id,
        sessionId = sessionId,
        role = role.name,
        content = content,
        reasoningText = reasoningText,
        timestamp = timestamp,
        toolName = toolName,
        toolCallId = toolCallId,
        toolStatus = toolStatus?.name,
        isStreaming = isStreaming,
        attachmentsJson = HermesJson.instance.encodeToString(attachments.orEmpty()),
        displayKind = displayKind,
    )
