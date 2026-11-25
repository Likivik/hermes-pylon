package com.m57.hermescontrol.ui.chat

/**
 * Rows already held that an older page re-requests at the seam, so the join can be verified. Small enough to
 * cost one page's worth of rows, large enough to survive a couple of inserts above the window.
 */
const val OLDER_PAGE_OVERLAP: Int = 3

/**
 * The widest seam view a re-anchor will ask for. A page that still does not join at this width is reported
 * broken rather than retried forever.
 */
const val OLDER_PAGE_OVERLAP_MAX: Int = 24

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
    /**
     * Rows we already hold, re-requested at the seam so the join can be verified. See [stitchOlderPage].
     * Zero disables the check.
     */
    val overlap: Int = 0,
) {
    /**
     * Where to actually start reading: the page, pulled back across the seam so the rows we already hold are
     * in the response. The two paging modes put "older" on opposite sides, so only `latest` needs the pull-back.
     */
    val fetchOffset: Int get() = if (useLatestPaging) (offset - overlap).coerceAtLeast(0) else offset

    /** What to actually ask the gateway for: the page, plus the seam to check it against. */
    val fetchLimit: Int get() = limit + overlap
}

/** Returns the page to request, or `null` when there is nothing to load. */
fun planOlderLoad(
    hasOlderMessages: Boolean,
    isLoadingOlder: Boolean,
    latestPaging: Boolean,
    loadedOffset: Int,
    pageSize: Int,
    overlap: Int = 0,
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

    return OlderPageRequest(
        offset = offset,
        limit = limit,
        useLatestPaging = latestPaging,
        overlap = overlap.coerceAtMost(pageSize),
    )
}
