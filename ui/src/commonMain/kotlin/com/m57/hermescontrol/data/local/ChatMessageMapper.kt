package com.m57.hermescontrol.data.local

import com.m57.hermescontrol.data.model.Attachment
import com.m57.hermescontrol.data.remote.HermesJson
import com.m57.hermescontrol.ui.chat.ChatMessage
import com.m57.hermescontrol.ui.chat.MessageRole
import com.m57.hermescontrol.ui.chat.ToolStatus

/**
 * Converts a stored row to the UI [ChatMessage].
 *
 * Keep this as a pure mapping — no I/O, no side effects. The reverse direction
 * is Android-only ([ChatMessageEntity]) because it builds a Room entity.
 */
fun ChatMessageRow.toUiModel(): ChatMessage =
    ChatMessage(
        id = id,
        role =
            when (role) {
                "USER" -> MessageRole.USER
                "ASSISTANT" -> MessageRole.ASSISTANT
                "SYSTEM" -> MessageRole.SYSTEM
                "TOOL" -> MessageRole.TOOL
                else -> MessageRole.ASSISTANT
            },
        content = content,
        reasoningText = reasoningText,
        timestamp = timestamp,
        isStreaming = isStreaming,
        attachments = decodeAttachments(attachmentsJson),
        toolName = toolName,
        toolCallId = toolCallId,
        toolStatus =
            when (toolStatus) {
                "RUNNING" -> ToolStatus.RUNNING
                "COMPLETED" -> ToolStatus.COMPLETED
                "FAILED" -> ToolStatus.FAILED
                else -> null
            },
        displayKind = displayKind,
    )

private fun decodeAttachments(value: String): List<Attachment> =
    runCatching {
        HermesJson.instance.decodeFromString<List<Attachment>>(value)
    }.getOrDefault(emptyList())
