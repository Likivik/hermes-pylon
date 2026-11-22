package com.m57.hermescontrol.ui.chat

/**
 * Which page "load older" should ask for, as data.
 *
 * The rules were four conditionals inside `loadOlderMessages`, mixed in with fence capture, owner
 * tokens and job bookkeeping — so a wrong offset could only be observed by scrolling a real session.
 *
 * Two paging modes exist and they run in opposite directions:
 *  - `latest`: the gateway pages forward from the newest end, so "older" means a *higher* offset;
 *  - otherwise: the page is an absolute window, so "older" means a *lower* offset, clamped at zero.
 *
 * In the second mode the last page is short, and its limit must be the distance actually travelled —
 * asking for a full page below zero would be a lie the gateway cannot satisfy.
 */
data class OlderPageRequest(
    val offset: Int,
    val limit: Int,
    val useLatestPaging: Boolean,
)

/** Returns the page to request, or `null` when there is nothing to load. */
fun planOlderLoad(
    hasOlderMessages: Boolean,
    isLoadingOlder: Boolean,
    latestPaging: Boolean,
    loadedOffset: Int,
    pageSize: Int,
): OlderPageRequest? {
    if (!hasOlderMessages) return null
    if (isLoadingOlder) return null
    if (!latestPaging && loadedOffset <= 0) return null

    val offset =
        if (latestPaging) {
            loadedOffset + pageSize
        } else {
            (loadedOffset - pageSize).coerceAtLeast(0)
        }
    val limit = if (latestPaging) pageSize else loadedOffset - offset

    return OlderPageRequest(offset = offset, limit = limit, useLatestPaging = latestPaging)
}
