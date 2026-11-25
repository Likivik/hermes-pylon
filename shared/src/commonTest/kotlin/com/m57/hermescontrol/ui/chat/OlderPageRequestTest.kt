package com.m57.hermescontrol.ui.chat

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The two paging modes put "older" on opposite sides, so the seam row re-request moves in opposite directions
 * too. Getting this backwards fetches a window that can never contain the rows we already hold, and the seam
 * check then fails forever — the bug that cost three attempts to find.
 */
class OlderPageRequestTest {
    @Test
    fun `latest pulls the offset back across the seam`() {
        val request =
            planOlderLoad(
                hasOlderMessages = true,
                isLoadingOlder = false,
                latestPaging = true,
                loadedOffset = 0,
                pageSize = 150,
                overlap = 3,
            )!!

        assertEquals(150, request.offset, "the page itself is unchanged")
        assertEquals(147, request.fetchOffset, "the read starts earlier, inside the rows we already hold")
        assertEquals(153, request.fetchLimit, "the page plus the seam rows")
    }

    @Test
    fun `the windowed mode extends the limit instead, because its older is the low side`() {
        val request =
            planOlderLoad(
                hasOlderMessages = true,
                isLoadingOlder = false,
                latestPaging = false,
                loadedOffset = 300,
                pageSize = 150,
                overlap = 3,
            )!!

        assertEquals(150, request.offset, "the page starts below what we hold")
        assertEquals(
            150,
            request.fetchOffset,
            "no pull-back here: the seam is at the high end, which the limit covers",
        )
        assertEquals(153, request.fetchLimit)
    }

    @Test
    fun `with no overlap the fetch window is exactly the page`() {
        val request =
            planOlderLoad(
                hasOlderMessages = true,
                isLoadingOlder = false,
                latestPaging = true,
                loadedOffset = 0,
                pageSize = 150,
            )!!

        assertEquals(request.offset, request.fetchOffset)
        assertEquals(request.limit, request.fetchLimit)
    }

    @Test
    fun `an overlap wider than the page is clamped to the page`() {
        val request =
            planOlderLoad(
                hasOlderMessages = true,
                isLoadingOlder = false,
                latestPaging = true,
                loadedOffset = 0,
                pageSize = 150,
                overlap = 999,
            )!!

        assertEquals(150, request.overlap, "we never re-request more rows than one page is worth")
    }
}
