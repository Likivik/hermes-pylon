package com.m57.hermescontrol.ui.chat

import com.m57.hermescontrol.data.model.Attachment
import com.m57.hermescontrol.data.model.AttachmentSource

/**
 * Resume-payload messages, as pure data.
 *
 * A resume ack carries the session's history as raw maps, not as the typed rows the REST route returns,
 * so the client has to decide: which role is this, which field holds the text, what happens to a message
 * with no content, and how a media directive is split out of the body.
 *
 * That decision used to be written inline inside a 127-line ViewModel method, reachable only through a
 * live socket. It is a function of its arguments — session id, payload, clock — so it is a function here.
 */
fun parseResumeMessages(
    sessionId: String,
    raw: List<*>,
    nowMillis: Long,
): List<ChatMessage> {
    return collapseDuplicateRows(
        raw.mapIndexedNotNull { index, item ->
            val message =
                item as? Map<*, *> ?: return@mapIndexedNotNull null
            val roleName = (message["role"] as? String)?.lowercase()
            val role =
                when (roleName) {
                    "user" -> MessageRole.USER
                    "system" -> MessageRole.SYSTEM
                    "tool" -> MessageRole.TOOL
                    "assistant" -> MessageRole.ASSISTANT
                    else -> return@mapIndexedNotNull null
                }
            val content =
                (message["text"] as? String)
                    ?: (message["content"] as? String)
                    ?: if (role == MessageRole.TOOL) {
                        (message["context"] as? String)
                            ?.takeIf { it.isNotBlank() }
                            ?: (message["name"] as? String).orEmpty()
                    } else {
                        ""
                    }
            if (content.isBlank()) return@mapIndexedNotNull null
            val reasoning =
                (message["reasoning"] as? String)
                    ?: (message["reasoning_text"] as? String).orEmpty()
            val timestampSeconds =
                when (val timestamp = message["timestamp"]) {
                    is Number -> timestamp.toDouble()
                    is String -> timestamp.toDoubleOrNull()
                    else -> null
                }
            val mediaItems =
                if (role == MessageRole.ASSISTANT) {
                    HostMediaExtractor.extract(content)
                } else {
                    emptyList()
                }
            val attachments =
                mediaItems
                    .map { item ->
                        Attachment(
                            uri = item.path,
                            name = mediaNameFromPath(item.path),
                            mimeType = mediaMimeForPath(item.path),
                            size = 0,
                            gatewayPath = item.path,
                            source = AttachmentSource.GATEWAY,
                        )
                    }.takeIf { it.isNotEmpty() }
            val rowId =
                (message["row_id"] as? Number)?.toLong()
                    ?: (message["row_id"] as? String)?.toLongOrNull()
            ChatMessage(
                // Identity follows the durable handle the resume ack carries. The index is a last resort for a
                // payload without `row_id`: it is positional, so it shifts whenever the payload's row set changes.
                id = rowId?.let { "$sessionId-row-$it" } ?: "resume-$sessionId-$index",
                origin = MessageOrigin.Resume(rowId),
                role = role,
                content =
                    if (mediaItems.isEmpty()) {
                        content
                    } else {
                        HostMediaExtractor.strip(content)
                    },
                reasoningText = reasoning,
                attachments = attachments,
                timestamp =
                    timestampSeconds
                        ?.times(1000)
                        ?.toLong()
                        ?: nowMillis + index,
                isStreaming = false,
                toolName =
                    if (role == MessageRole.TOOL) {
                        (message["tool_name"] as? String)
                            ?: (message["name"] as? String)
                    } else {
                        null
                    },
                toolCallId =
                    if (role == MessageRole.TOOL) {
                        (message["tool_call_id"] as? String)
                            ?: (message["tool_id"] as? String)
                    } else {
                        null
                    },
                displayKind = message["display_kind"] as? String,
                toolStatus = if (role == MessageRole.TOOL) ToolStatus.COMPLETED else null,
            )
        },
    )
}
