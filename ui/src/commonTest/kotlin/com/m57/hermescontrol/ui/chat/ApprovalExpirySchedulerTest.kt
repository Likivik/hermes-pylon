package com.m57.hermescontrol.ui.chat

import com.m57.hermescontrol.data.ws.PrivilegedRequestBinding
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The scheduler decides when approval buttons disappear. Getting it wrong is user-visible in both
 * directions: a timer that never fires leaves buttons for a request that no longer exists, and one
 * that fires late clears the controls of a *newer* approval for the same message.
 */
class ApprovalExpirySchedulerTest {
    private fun binding(
        requestId: String = "req-1",
        generation: Int = 1,
    ) = PrivilegedRequestBinding(
        requestId = requestId,
        runtimeSessionId = "rt-1",
        profileId = "default",
        connectionGeneration = generation,
    )

    @Test
    fun expiryFiresOnceAfterTheTimeoutAndDisarmsItself() =
        runTest {
            val scheduler = ApprovalExpiryScheduler(this)
            val fired = mutableListOf<String>()
            scheduler.schedule("msg-1", binding(), timeoutSeconds = 2.0) { id, _ -> fired += id }
            assertEquals(1, scheduler.active)

            testScheduler.advanceTimeBy(1_500)
            assertTrue(fired.isEmpty())

            testScheduler.advanceTimeBy(700)
            assertEquals(listOf("msg-1"), fired)
            assertEquals(0, scheduler.active)
        }

    @Test
    fun reschedulingTheSameMessageReplacesTheEarlierTimer() =
        runTest {
            val scheduler = ApprovalExpiryScheduler(this)
            val fired = mutableListOf<Double>()
            // Same message and same binding: the retry path. The first timer must be replaced.
            scheduler.schedule("msg-1", binding(), timeoutSeconds = 1.0) { _, _ -> fired += 1.0 }
            scheduler.schedule("msg-1", binding(), timeoutSeconds = 5.0) { _, _ -> fired += 2.0 }

            // The first timer must not fire at its own deadline: it was replaced.
            testScheduler.advanceTimeBy(1_500)
            assertTrue(fired.isEmpty())
            assertEquals(1, scheduler.active)

            testScheduler.advanceTimeBy(4_000)
            assertEquals(listOf(2.0), fired)
        }

    @Test
    fun cancelAllPreventsAnyTimerFromFiring() =
        runTest {
            val scheduler = ApprovalExpiryScheduler(this)
            var fired = 0
            scheduler.schedule("msg-1", binding(), timeoutSeconds = 1.0) { _, _ -> fired++ }
            scheduler.schedule("msg-2", binding(requestId = "req-2"), timeoutSeconds = 1.0) { _, _ -> fired++ }

            scheduler.cancelAll()
            testScheduler.advanceTimeBy(5_000)

            assertEquals(0, fired)
            assertEquals(0, scheduler.active)
        }

    @Test
    fun differentBindingsForTheSameMessageAreIndependent() =
        runTest {
            val scheduler = ApprovalExpiryScheduler(this)
            val fired = mutableListOf<Int>()
            scheduler.schedule(
                "msg-1",
                binding(generation = 1),
                timeoutSeconds = 1.0,
            ) { _, b -> fired += b.connectionGeneration }
            scheduler.schedule(
                "msg-1",
                binding(generation = 2),
                timeoutSeconds = 3.0,
            ) { _, b -> fired += b.connectionGeneration }

            testScheduler.advanceTimeBy(1_500)
            assertEquals(listOf(1), fired)

            testScheduler.advanceTimeBy(2_000)
            assertEquals(listOf(1, 2), fired)
        }
}
