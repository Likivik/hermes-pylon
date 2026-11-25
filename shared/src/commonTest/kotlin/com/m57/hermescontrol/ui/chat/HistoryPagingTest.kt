package com.m57.hermescontrol.ui.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Paging arithmetic, tested as data.
 *
 * Every case here used to be a conditional inside a 72-line method that also captured fences, bumped
 * owner tokens and launched jobs — so the arithmetic could only be observed by scrolling a live session.
 */
class HistoryPagingTest {
    private fun plan(
        hasOlder: Boolean = true,
        loading: Boolean = false,
        latest: Boolean = true,
        offset: Int = 0,
        pageSize: Int = 150,
    ) = planOlderLoad(
        hasOlderMessages = hasOlder,
        isLoadingOlder = loading,
        latestPaging = latest,
        loadedOffset = offset,
        pageSize = pageSize,
    )

    @Test
    fun latestPagingAsksForThePageAboveTheLoadedWindow() {
        val request = plan(latest = true, offset = 0)

        assertEquals(150, request?.offset)
        assertEquals(150, request?.limit)
        assertEquals(true, request?.useLatestPaging)
    }

    @Test
    fun latestPagingKeepsSteppingForward() {
        val request = plan(latest = true, offset = 300)

        assertEquals(450, request?.offset)
        assertEquals(150, request?.limit)
    }

    @Test
    fun absolutePagingStepsBackwardsByOnePage() {
        val request = plan(latest = false, offset = 450)

        assertEquals(300, request?.offset)
        assertEquals(150, request?.limit)
        assertEquals(false, request?.useLatestPaging)
    }

    @Test
    fun theLastShortPageAsksOnlyForWhatIsLeft() {
        val request = plan(latest = false, offset = 100)

        assertEquals(0, request?.offset)
        assertEquals(100, request?.limit, "asking for 150 below zero would be a page the gateway cannot serve")
    }

    @Test
    fun absolutePagingAtTheTopLoadsNothing() {
        assertNull(plan(latest = false, offset = 0))
    }

    @Test
    fun aSessionWithNoOlderMessagesLoadsNothing() {
        assertNull(plan(hasOlder = false, latest = true, offset = 500))
    }

    @Test
    fun anInFlightLoadIsNotDuplicated() {
        assertNull(plan(loading = true, offset = 500))
    }

    @Test
    fun aCustomPageSizeIsHonoured() {
        val request = plan(latest = true, offset = 20, pageSize = 20)

        assertEquals(40, request?.offset)
        assertEquals(20, request?.limit)
    }
}
