package com.m57.hermescontrol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The screen graph is assembled in `commonMain` for every target, and nothing
 * verified that until now: `:ui` tests existed but no CI job ran them, so a
 * definition that only constructs on one platform would ship.
 *
 * This asserts structure, not rendering — every screen object is built and the
 * graph's invariants hold on the JVM and, through the same `commonTest` sources,
 * on wasmJs via karma.
 */
class ScreenGraphTest {
    private val graph = screenGraph()

    @Test
    fun everyTargetRegistersScreens() {
        assertTrue(graph.screens.isNotEmpty(), "the graph is empty")
    }

    @Test
    fun navKeysAreUnique() {
        val keys = graph.screens.map { it.key }
        assertEquals(keys.size, keys.toSet().size, "duplicate NavKey in the graph: $keys")
    }

    @Test
    fun everyPrimaryKeyIsRegistered() {
        // `primaryScreens` is private in ScreenGraphEntries.kt; assert through the
        // public surface so the test cannot grow a dependency on internals.
        val orphans = graph.primary.filter { graph.definitionFor(it) == null }
        assertTrue(orphans.isEmpty(), "primary keys with no definition: $orphans")
    }

    @Test
    fun primaryKeysAnswerIsPrimary() {
        graph.primary.forEach { assertTrue(graph.isPrimary(it), "$it is primary but isPrimary() says no") }
    }
}
