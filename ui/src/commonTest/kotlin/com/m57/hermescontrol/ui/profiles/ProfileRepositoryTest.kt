package com.m57.hermescontrol.ui.profiles

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The repository is the only writer of the selection; a failed observation keeps the last
 * snapshot instead of inventing one.
 */
class ProfileRepositoryTest {
    private val selection = MutableStateFlow<String?>(null)
    private var applied: String? = "<untouched>"

    private fun repository(serving: suspend () -> String? = { "default" }) =
        ProfileRepository(fetchServing = serving, apply = { applied = it }, selection = selection)

    @Test
    fun selectingWritesThrough() {
        repository().select("work")

        assertEquals("work", applied)
    }

    @Test
    fun aBlankSelectionClears() {
        repository().select("   ")

        assertNull(applied)
    }

    @Test
    fun observingRecordsWhatTheGatewayServes() =
        runTest {
            val repo = repository { "default" }

            val observed = repo.refreshCurrent()

            assertEquals("default", observed?.profile)
            assertEquals("default", repo.lastObservedCurrent.value?.profile)
        }

    @Test
    fun aFailedObservationKeepsThePreviousSnapshot() =
        runTest {
            val repo = repository { "default" }
            repo.refreshCurrent()
            val failing = repository { throw IllegalStateException("offline") }

            failing.refreshCurrent()

            assertEquals("default", repo.lastObservedCurrent.value?.profile)
            assertNull(failing.lastObservedCurrent.value)
        }

    @Test
    fun divergenceNeedsBothSidesKnown() {
        val repo = repository()

        assertFalse(repo.isDivergent())

        selection.value = "work"
        repo.select("work")

        assertFalse(repo.isDivergent(), "nothing observed yet")
    }

    @Test
    fun divergenceIsVisibleOnceObserved() =
        runTest {
            selection.value = "work"
            val repo = repository { "default" }

            repo.refreshCurrent()

            assertTrue(repo.isDivergent())
        }
}
