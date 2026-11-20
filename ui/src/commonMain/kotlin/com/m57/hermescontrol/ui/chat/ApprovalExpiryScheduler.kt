package com.m57.hermescontrol.ui.chat

import com.m57.hermescontrol.data.ws.PrivilegedRequestBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Local expiry timers for live approval cards.
 *
 * The gateway publishes the exact lifetime its waiting thread uses (`timeoutSeconds`), so the card
 * disappears when the request it would answer is already gone — rather than lingering and resolving
 * nothing.
 *
 * Two properties this class exists to keep:
 *
 * - **Request ids can be reused**, so a timer is owned by `(messageId, binding)`, not by request id
 *   alone. A new approval for the same message replaces the old timer instead of racing it.
 * - **Expiry is conditional**: a timer removes its own entry only if that entry is still its job, so
 *   a replaced or cancelled timer can never clear a newer approval's controls.
 */
internal class ApprovalExpiryScheduler(
    private val scope: CoroutineScope,
) {
    private data class Key(
        val messageId: String,
        val binding: PrivilegedRequestBinding,
    )

    private val jobs = mutableMapOf<Key, Job>()

    /** Timers currently armed. Exposed for tests and diagnostics. */
    val active: Int get() = jobs.size

    fun schedule(
        messageId: String,
        binding: PrivilegedRequestBinding,
        timeoutSeconds: Double,
        onExpire: (messageId: String, binding: PrivilegedRequestBinding) -> Unit,
    ) {
        val key = Key(messageId, binding)
        val job =
            scope.launch {
                delay((timeoutSeconds * 1_000.0).toLong().coerceAtLeast(1L))
                if (jobs[key] === coroutineContext[Job]) jobs.remove(key)
                onExpire(messageId, binding)
            }
        jobs.put(key, job)?.cancel()
    }

    fun cancel(
        messageId: String,
        binding: PrivilegedRequestBinding,
    ) {
        jobs.remove(Key(messageId, binding))?.cancel()
    }

    fun cancelAll() {
        jobs.values.forEach(Job::cancel)
        jobs.clear()
    }
}
