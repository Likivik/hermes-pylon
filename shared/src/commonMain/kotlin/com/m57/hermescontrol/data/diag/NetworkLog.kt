package com.m57.hermescontrol.data.diag

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.TimeSource

/**
 * One completed request: what was asked, how it ended, and how long it took.
 *
 * [outcome] is deliberately a string rather than a sealed type: it has to carry
 * both `HTTP 401` and `RequestTimeoutException` without the recorder knowing
 * every error type the app can throw.
 */
data class NetworkEvent(
    val seq: Long,
    val method: String,
    val url: String,
    val outcome: String,
    val durationMillis: Long,
) {
    /** True when the request never produced a response. */
    val isTransportFailure: Boolean
        get() = !outcome.startsWith("HTTP ")
}

/**
 * Bounded, process-wide record of recent gateway traffic.
 *
 * Why this exists: the failure that started it was a 30 s `Request timeout has
 * expired` against a `/api/status` that answered in 20 ms from the shell. The
 * only way to tell that apart from a wrong URL, a proxy that never closes, or a
 * missing session cookie is to see the requests the app itself made. Log lines
 * are unavailable to a phone user; this is the same information, on screen.
 *
 * The buffer is small on purpose (diagnostics, not telemetry) and the newest
 * event is last so the list reads top-down in time order.
 */
object NetworkLog {
    const val CAPACITY: Int = 40

    private val _events = MutableStateFlow<List<NetworkEvent>>(emptyList())
    val events: StateFlow<List<NetworkEvent>> = _events.asStateFlow()

    /**
     * Marks the start of a request. The returned marker is turned into a
     * duration by [record]; using a monotonic [TimeSource] rather than a clock
     * keeps every target working, including wasm where `kotlin.time.Clock` is
     * still experimental.
     */
    fun start(): TimeSource.Monotonic.ValueTimeMark = TimeSource.Monotonic.markNow()

    fun record(
        method: String,
        url: String,
        outcome: String,
        mark: TimeSource.Monotonic.ValueTimeMark,
    ) {
        val event =
            NetworkEvent(
                seq = 0,
                method = method,
                url = url,
                outcome = outcome,
                durationMillis = mark.elapsedNow().inWholeMilliseconds,
            )
        _events.update { current ->
            val next = current + event
            val trimmed = if (next.size > CAPACITY) next.takeLast(CAPACITY) else next
            // Re-number so seq is dense and monotonic even after trimming.
            trimmed.mapIndexed { index, e -> e.copy(seq = index + 1L) }
        }
    }

    fun clear() {
        _events.value = emptyList()
    }
}
