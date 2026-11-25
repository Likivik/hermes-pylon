package com.m57.hermescontrol.ui.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The sync guard, tested as data.
 *
 * Each refusal here is a bug that was previously only findable by watching a real session: sync while a
 * load is in flight duplicates rows, sync while streaming yanks the transcript.
 */
class SyncRequestTest {
    private fun plan(
        syncing: Boolean = false,
        loading: Boolean = false,
        loadingOlder: Boolean = false,
        agentTyping: Boolean = false,
        streaming: Boolean = false,
        latest: Boolean = true,
        indexes: List<Int> = emptyList(),
        loadedOffset: Int = 0,
        pageSize: Int = 150,
    ) = planSync(
        isSyncing = syncing,
        isLoading = loading,
        isLoadingOlder = loadingOlder,
        isAgentTyping = agentTyping,
        isStreaming = streaming,
        latestPaging = latest,
        serverIndexes = indexes,
        loadedOffset = loadedOffset,
        pageSize = pageSize,
    )

    @Test
    fun aQuietTranscriptSyncsFromTheNewestEnd() {
        val request = plan(latest = true)

        assertEquals(0, request?.offset)
        assertEquals(150, request?.limit)
        assertEquals(true, request?.useLatestPaging)
    }

    @Test
    fun absolutePagingContinuesAfterTheHighestServerIndex() {
        val request = plan(latest = false, indexes = listOf(3, 7, 12), loadedOffset = 40)

        assertEquals(13, request?.offset, "sync must continue after 12, not after the loaded offset")
        assertEquals(150, request?.limit)
    }

    @Test
    fun absolutePagingWithNoServerRowsFallsBackToTheLoadedOffset() {
        val request = plan(latest = false, indexes = emptyList(), loadedOffset = 300)

        assertEquals(300, request?.offset)
    }

    @Test
    fun aSyncAlreadyInFlightIsNotRepeated() {
        assertNull(plan(syncing = true))
    }

    @Test
    fun nothingSyncsWhileAPageIsLoading() {
        assertNull(plan(loading = true))
        assertNull(plan(loadingOlder = true))
    }

    @Test
    fun nothingSyncsWhileTheAgentIsTypingOrStreaming() {
        assertNull(plan(agentTyping = true))
        assertNull(plan(streaming = true), "a sync mid-stream replaces the transcript the user is reading")
    }
}
