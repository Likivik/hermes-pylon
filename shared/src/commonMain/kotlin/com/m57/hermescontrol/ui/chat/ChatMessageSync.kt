package com.m57.hermescontrol.ui.chat

/**
 * Reconciles optimistic WebSocket messages with stable REST history rows.
 */
fun mergeSyncedMessages(
    current: List<ChatMessage>,
    incoming: List<ChatMessage>,
): List<ChatMessage> {
    val unmatchedIncoming = incoming.toMutableList()
    val merged = mutableListOf<ChatMessage>()
    val localRunningToolCounts =
        current
            .filter {
                it.origin !is MessageOrigin.Rest &&
                    it.role == MessageRole.TOOL &&
                    it.toolStatus == ToolStatus.RUNNING &&
                    it.toolName != null
            }.groupingBy { it.toolName }
            .eachCount()
    val incomingToolCounts =
        incoming
            .filter { it.role == MessageRole.TOOL && it.toolName != null }
            .groupingBy { it.toolName }
            .eachCount()

    for (existing in current) {
        val matchIndex =
            if (existing.origin is MessageOrigin.Rest) {
                unmatchedIncoming.indexOfFirst { it.id == existing.id }
            } else {
                unmatchedIncoming.indexOfFirst { candidate ->
                    val sameRole = candidate.role == existing.role
                    val sameCallId =
                        existing.toolCallId != null &&
                            existing.toolCallId == candidate.toolCallId
                    val conflictingCallIds =
                        existing.toolCallId != null &&
                            candidate.toolCallId != null &&
                            existing.toolCallId != candidate.toolCallId
                    val unambiguousLegacyTool =
                        existing.role == MessageRole.TOOL &&
                            existing.toolStatus == ToolStatus.RUNNING &&
                            existing.toolCallId == null &&
                            candidate.toolCallId == null &&
                            candidate.toolName != null &&
                            candidate.toolName == existing.toolName &&
                            localRunningToolCounts[existing.toolName] == 1 &&
                            incomingToolCounts[candidate.toolName] == 1
                    val attachmentEnrichedUserMessage =
                        sameRole &&
                            existing.role == MessageRole.USER &&
                            sameUserMessageWithAttachmentEnrichment(
                                localContent = existing.content,
                                serverContent = candidate.content,
                            )
                    sameRole && (
                        sameCallId ||
                            (
                                !conflictingCallIds &&
                                    (
                                        candidate.content == existing.content ||
                                            attachmentEnrichedUserMessage
                                    )
                            ) ||
                            unambiguousLegacyTool
                    )
                }
            }
        if (matchIndex >= 0) {
            merged.add(unmatchedIncoming.removeAt(matchIndex))
        } else {
            merged.add(existing)
        }
    }
    merged.addAll(unmatchedIncoming)
    return merged.distinctBy { it.id }
}

/**
 * Matches a local optimistic user row to the server's enriched transcript row.
 * Only the incoming server row is normalized so literal reference lines in a
 * locally authored message continue to require an exact match.
 */
private fun sameUserMessageWithAttachmentEnrichment(
    localContent: String,
    serverContent: String,
): Boolean {
    val normalizedServer =
        serverContent
            .lines()
            .filterNot(::isAttachmentReferenceLine)
            .joinToString("\n")
            .trim()
    val normalizedOriginal = serverContent.trim()
    if (normalizedServer == normalizedOriginal) return false

    return localContent.trim() == normalizedServer
}

private fun isAttachmentReferenceLine(line: String): Boolean {
    val normalized = line.trim()
    return normalized.startsWith("@image:") ||
        normalized.startsWith("@file:") ||
        normalized == "[screenshot]"
}

/**
 * Whether two transcripts are the same for the purpose of skipping a write.
 *
 * This is what stops a sync from replacing the list the user is reading with an identical one, which would
 * restart list animations and lose scroll anchoring. It has to compare every field a bubble renders, not just
 * id and text: a tool row that changes from RUNNING to COMPLETED is a different row even when its content is
 * unchanged, and skipping that write would leave a finished tool spinning.
 */
fun sameTranscript(
    left: List<ChatMessage>,
    right: List<ChatMessage>,
): Boolean =
    left.size == right.size &&
        left.zip(right).all { (a, b) ->
            a.id == b.id &&
                a.role == b.role &&
                a.content == b.content &&
                a.reasoningText == b.reasoningText &&
                a.attachments == b.attachments &&
                a.toolName == b.toolName &&
                a.toolCallId == b.toolCallId &&
                a.toolStatus == b.toolStatus &&
                a.displayKind == b.displayKind
        }
