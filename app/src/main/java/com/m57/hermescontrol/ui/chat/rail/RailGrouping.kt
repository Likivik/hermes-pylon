package com.m57.hermescontrol.ui.chat.rail

import com.m57.hermescontrol.ui.chat.SessionUi

/**
 * Pure rail grouping/ordering — no Compose, fully unit-testable.
 *
 * Invariants (each covered by RailGroupingTest):
 *  - Pinned ALWAYS render, even if stale; they sort first, by persisted order
 *    (falling back to input order for ids missing from the order list).
 *  - Stale-unpinned go to the archive group (drawer at the bottom).
 *  - Fresh (not pinned, not stale) fill the middle.
 *  - Manual order may only arrange within a group; group membership is by
 *    pinned/stale flags and can never be changed by dragging.
 */
data class RailGroups(
    val pinned: List<SessionUi>,
    val fresh: List<SessionUi>,
    val stale: List<SessionUi>,
) {
    val all: List<SessionUi> get() = pinned + fresh + stale
    val isEmpty: Boolean get() = all.isEmpty()
}

private fun byOrder(group: List<SessionUi>, order: List<String>): List<SessionUi> {
    val idx = order.withIndex().associate { it.value to it.index }
    return group.sortedWith(compareBy({ idx[it.id] ?: Int.MAX_VALUE }))
}

fun groupRail(sessions: List<SessionUi>, pinnedIds: Set<String>, order: List<String>): RailGroups {
    val pinned = byOrder(sessions.filter { it.id in pinnedIds }, order)
    val unpinned = sessions.filter { it.id !in pinnedIds }
    val fresh = byOrder(unpinned.filter { !it.isStale }, order)
    val stale = byOrder(unpinned.filter { it.isStale }, order)
    return RailGroups(pinned, fresh, stale)
}

/**
 * Merge a live drag arrangement [arrangedIds] with the canonical [groups].
 * Returns the full pinned+fresh+stale order that should be persisted.
 * Guards: drags can only move ids that exist; unknown/reordered-into-wrong-
 * group ids are ignored and each group's canonical membership wins.
 */
fun mergeArrangement(groups: RailGroups, arrangedIds: List<String>): List<String> {
    val arrangeWithin = { group: List<SessionUi> ->
        val memberIds = group.map { it.id }.toSet()
        val arranged = arrangedIds.filter { it in memberIds }
        val rest = group.map { it.id }.filter { it !in arrangedIds }
        arranged + rest
    }
    return arrangeWithin(groups.pinned) +
        arrangeWithin(groups.fresh) +
        arrangeWithin(groups.stale)
}
