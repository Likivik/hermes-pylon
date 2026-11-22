package com.m57.hermescontrol.ui.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The first-load decision, tested as data.
 *
 * Three gateways, three behaviours: one that supports latest ordering, one that does not but reports a
 * count, and one that reports neither — where the honest answer is to abort rather than invent an offset.
 */
class FirstPagePlanTest {
    private fun plan(
        probeSucceeded: Boolean = true,
        probeOrder: String? = "latest",
        count: Int? = null,
        pageSize: Int = 150,
    ) = planFirstLoad(probeSucceeded, probeOrder, count, pageSize)

    @Test
    fun aGatewayThatSupportsLatestOrderingOpensOnTheNewestPage() {
        val plan = plan(probeOrder = "latest")

        assertEquals(true, plan?.useLatestPaging)
        assertEquals(0, plan?.offset)
    }

    @Test
    fun aGatewayWithoutLatestOrderingAsksForTheWindowEndingAtTheNewestMessage() {
        val plan = plan(probeOrder = null, count = 500)

        assertEquals(false, plan?.useLatestPaging)
        assertEquals(350, plan?.offset, "500 messages, 150 per page: the last full window starts at 350")
    }

    @Test
    fun aShortSessionAsksFromZeroRatherThanBelowIt() {
        val plan = plan(probeOrder = null, count = 80)

        assertEquals(0, plan?.offset)
    }

    @Test
    fun anUnknownCountWithoutLatestOrderingAborts() {
        assertNull(
            plan(probeOrder = null, count = null),
            "with no ordering and no count there is no page to ask for, so the load must stop",
        )
    }

    @Test
    fun aFailedProbeFallsBackToTheFirstPage() {
        val plan = plan(probeSucceeded = false, probeOrder = null, count = null)

        assertEquals(false, plan?.useLatestPaging)
        assertEquals(0, plan?.offset)
    }
}
