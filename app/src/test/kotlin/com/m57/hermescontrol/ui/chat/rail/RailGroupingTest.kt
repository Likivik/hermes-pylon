package com.m57.hermescontrol.ui.chat.rail

import com.m57.hermescontrol.ui.chat.SessionUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression source-of-truth for rail grouping. The empty-rail bug
 * (render-21) came from lastActive==0 counting as stale — locked here.
 */
class RailGroupingTest {

    private fun s(id: String, lastActive: Long = 0L) = SessionUi(id = id, title = "T$id", lastActive = lastActive)

    @Test
    fun `unknown lastActive is fresh not stale`() {
        assertTrue(!s("a", 0).isStale)
    }

    @Test
    fun `all-unknown-activity sessions render in fresh group`() {
        // The exact render-21 failure: 6 sessions with lastActive=0 must show,
        // none may land in the archive.
        val sessions = (1..6).map { s("s$it") }
        val groups = groupRail(sessions, pinnedIds = emptySet(), order = emptyList())
        assertEquals(6, groups.fresh.size)
        assertEquals(0, groups.stale.size)
        assertEquals(0, groups.pinned.size)
        assertEquals(6, groups.all.size)
    }

    @Test
    fun `old sessions go to stale group`() {
        val week = 7L * 24 * 60 * 60
        val old = s("old", System.currentTimeMillis() / 1000 - week - 60)
        val new = s("new", System.currentTimeMillis() / 1000)
        val groups = groupRail(listOf(old, new), emptySet(), emptyList())
        assertEquals(listOf(new), groups.fresh)
        assertEquals(listOf(old), groups.stale)
    }

    @Test
    fun `stale but pinned stays visible in pinned group`() {
        val week = 7L * 24 * 60 * 60
        val old = s("old", System.currentTimeMillis() / 1000 - week - 60)
        val groups = groupRail(listOf(old), pinnedIds = setOf("old"), order = emptyList())
        assertTrue(groups.stale.isEmpty())
        assertEquals(listOf(old), groups.pinned)
    }

    @Test
    fun `manual order sorts within groups only`() {
        val a = s("a"); val b = s("b"); val c = s("c")
        val groups = groupRail(listOf(a, b, c), emptySet(), listOf("c", "b", "a"))
        assertEquals(listOf(c, b, a), groups.fresh)
    }

    @Test
    fun `mergeArrangement preserves unknown ids and group membership`() {
        val a = s("a"); val b = s("b"); val c = s("c")
        val groups = groupRail(listOf(a, b, c), emptySet(), emptyList())
        val merged = mergeArrangement(groups, listOf("c", "a", "b", "ghost"))
        // ghost (unknown) ignored; group order persisted for known ids.
        assertEquals(listOf("c", "a", "b"), merged)
    }
}
