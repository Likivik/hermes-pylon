package com.m57.hermescontrol.ui.chat

import com.m57.hermescontrol.data.model.Attachment
import com.m57.hermescontrol.data.model.AttachmentSource
import com.m57.hermescontrol.data.model.SessionMessage
import com.m57.hermescontrol.util.GatewayPath

/**
 * Maps REST/server [SessionMessage]s onto the UI's [ChatMessage]s.
 *
 * Pure function: no I/O, no mutable state, nothing Android- or ViewModel-shaped.
 * Everything it needs is passed in — including the currently displayed messages
 * (used to preserve reasoning text across a refresh) and a clock reading, so
 * callers that want a fixed clock in a test do not have to reach for a
 * platform API. `System.currentTimeMillis()` is JVM-only and unavailable here;
 * production callers pass `nowMillis()`.
 *
 * Lives in `commonMain` so it is exercised on every target, wasmJs included.
 */
object ServerMessageMapper {
    fun map(
        sessionId: String,
        messages: List<SessionMessage>,
        existingMessages: List<ChatMessage>,
        offset: Int,
        useLatestPaging: Boolean,
        nowMillis: Long,
    ): List<ChatMessage> {
        val existingWithReasoning =
            existingMessages.filter { it.reasoningText.isNotBlank() }
        val existingReasoningById = existingWithReasoning.associateBy { it.id }
        val uniqueReasoningByContent =
            existingWithReasoning
                .groupBy { it.role to it.content }
                .mapNotNull { (key, matches) ->
                    matches.singleOrNull()?.let { key to it }
                }.toMap()
        val incomingRoleAndContentCounts =
            messages
                .filter(::isDisplayableServerMessage)
                .map { msg ->
                    val role =
                        when (msg.role?.lowercase()) {
                            "user" -> MessageRole.USER
                            "system" -> MessageRole.SYSTEM
                            "tool" -> MessageRole.TOOL
                            else -> MessageRole.ASSISTANT
                        }
                    role to msg.contentText
                }.groupingBy { it }
                .eachCount()

        return messages.mapIndexedNotNull { index, msg ->
            if (!isDisplayableServerMessage(msg)) {
                return@mapIndexedNotNull null
            }
            val role =
                when (msg.role?.lowercase()) {
                    "user" -> MessageRole.USER
                    "system" -> MessageRole.SYSTEM
                    "tool" -> MessageRole.TOOL
                    else -> MessageRole.ASSISTANT
                }
            val globalIndex = offset + index
            val id =
                if (useLatestPaging) {
                    msg.id?.let { "rest-$sessionId-$it" }
                        ?: "rest-$sessionId-$globalIndex"
                } else {
                    "rest-$sessionId-$globalIndex"
                }
            val content = msg.contentText
            val roleAndContent = role to content
            val preservedReasoning =
                existingReasoningById[id]
                    ?.takeIf { existing ->
                        existing.role == role && existing.content == content
                    }
                    ?: uniqueReasoningByContent[roleAndContent]
                        ?.takeIf { incomingRoleAndContentCounts[roleAndContent] == 1 }
            val reasoning =
                msg.reasoningText.ifBlank {
                    preservedReasoning?.reasoningText.orEmpty()
                }
            val timestamp =
                msg.timestampText
                    ?.toDoubleOrNull()
                    ?.times(1000)
                    ?.toLong()
                    ?: nowMillis
            var finalContent = content
            var attachments: List<Attachment>? = null
            if (role == MessageRole.ASSISTANT && content.contains("MEDIA:")) {
                val items = HostMediaExtractor.extract(content)
                if (items.isNotEmpty()) {
                    finalContent = HostMediaExtractor.strip(content)
                    attachments =
                        items
                            .mapNotNull { item ->
                                val path = GatewayPath.normalizePath(item.path) ?: return@mapNotNull null
                                Attachment(
                                    uri = path,
                                    name = mediaNameFromPath(item.path),
                                    mimeType = mediaMimeForPath(item.path),
                                    size = 0,
                                    gatewayPath = path,
                                    source = AttachmentSource.GATEWAY,
                                )
                            }.takeIf { it.isNotEmpty() }
                }
            }
            ChatMessage(
                id = id,
                role = role,
                content = finalContent,
                reasoningText = reasoning,
                attachments = attachments,
                timestamp = timestamp,
                isStreaming = false,
                toolName = msg.toolName,
                toolCallId = msg.toolCallId,
                toolStatus = if (role == MessageRole.TOOL) ToolStatus.COMPLETED else null,
                displayKind = msg.displayKind,
            )
        }
    }
}
