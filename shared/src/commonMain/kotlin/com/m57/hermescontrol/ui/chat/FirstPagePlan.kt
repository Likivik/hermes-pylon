package com.m57.hermescontrol.ui.chat

/**
 * Which page a freshly opened session should show.
 *
 * A gateway may or may not support `order = "latest"`. When it does, the first page is simply the newest
 * one. When it does not, the client has to learn how many messages exist and ask for the window that ends
 * at the newest message — `count - pageSize`, floored at zero so a short session asks for offset 0.
 *
 * The awkward case is a gateway without latest ordering that also will not tell us the count: there is no
 * honest page to request, so the load must stop rather than guess an offset. That abort used to be a
 * `return@launchHistoryLoad` buried in a 127-line method, indistinguishable from a network failure.
 */
data class FirstPagePlan(
    val useLatestPaging: Boolean,
    val offset: Int,
)

/**
 * @param probeSucceeded whether the `order = "latest"` probe returned a page at all.
 * @param probeOrder the `order` the gateway echoed back, if any.
 * @param messageCount the session's message count, or `null` when it could not be resolved.
 * @return the plan, or `null` when the load must abort because there is no page it can honestly ask for.
 */
fun planFirstLoad(
    probeSucceeded: Boolean,
    probeOrder: String?,
    messageCount: Int?,
    pageSize: Int,
): FirstPagePlan? {
    if (probeSucceeded && probeOrder == "latest") {
        return FirstPagePlan(useLatestPaging = true, offset = 0)
    }
    if (probeSucceeded) {
        val count = messageCount ?: return null
        return FirstPagePlan(useLatestPaging = false, offset = (count - pageSize).coerceAtLeast(0))
    }
    return FirstPagePlan(useLatestPaging = false, offset = 0)
}
