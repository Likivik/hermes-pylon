package com.m57.hermescontrol.ui.chat

/**
 * Whether a background sync should run, and from where.
 *
 * A sync must never fight another history operation: while a load is in flight, while the agent is
 * typing, or while a message is streaming, a sync would either duplicate rows or yank the transcript
 * out from under the user. Those five guards lived as one long boolean inside `syncCurrentSession`,
 * next to the offset arithmetic, and could only be exercised through the ViewModel.
 *
 * The offset itself has two modes, same as older-load:
 *  - `latest`: sync always asks from the newest end, offset 0;
 *  - otherwise: sync continues after the highest server index already in the transcript, falling back
 *    to the loaded offset when the transcript holds no server rows yet.
 */
data class SyncRequest(
    val offset: Int,
    val limit: Int,
    val useLatestPaging: Boolean,
)

/**
 * @param serverIndexes the server-side indexes of messages already in the transcript, if any.
 * @return the page to request, or `null` when this is a bad moment to sync.
 */
fun planSync(
    isSyncing: Boolean,
    isLoading: Boolean,
    isLoadingOlder: Boolean,
    isAgentTyping: Boolean,
    isStreaming: Boolean,
    latestPaging: Boolean,
    serverIndexes: List<Int>,
    loadedOffset: Int,
    pageSize: Int,
): SyncRequest? {
    if (isSyncing || isLoading || isLoadingOlder || isAgentTyping || isStreaming) return null

    val offset =
        if (latestPaging) {
            0
        } else {
            serverIndexes.maxOrNull()?.plus(1) ?: loadedOffset
        }

    return SyncRequest(offset = offset, limit = pageSize, useLatestPaging = latestPaging)
}
