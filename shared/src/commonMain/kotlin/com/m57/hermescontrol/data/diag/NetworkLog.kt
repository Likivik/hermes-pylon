package com.m57.hermescontrol.data.diag

import com.m57.hermescontrol.util.HermesLog
import com.m57.hermescontrol.util.LogRedaction
import com.m57.hermescontrol.util.LogSeverity
import kotlin.time.TimeSource

/**
 * Network events as log records.
 *
 * This used to be a second bounded buffer with its own `StateFlow`; it is now a
 * thin recorder in front of [HermesLog]'s ring, so the diagnostics screen, the
 * share bundle and any future sink read one list instead of two that can drift.
 * The typed `NetworkEvent` view is gone deliberately: a record with
 * `component=net` already carries method, URL, outcome and duration, and a second
 * representation is how "the log says 401 but the screen says timeout" happens.
 */
object NetworkLog {
    const val COMPONENT: String = "net"

    /**
     * Marks the start of a request. The returned marker is turned into a
     * duration by [record]; a monotonic [TimeSource] rather than a clock keeps
     * every target working, including wasm where `kotlin.time.Clock` is still
     * experimental.
     */
    fun start(): TimeSource.Monotonic.ValueTimeMark = TimeSource.Monotonic.markNow()

    /**
     * Records one completed request.
     *
     * Transport failures are `WARN` (the operation failed; a retry may still
     * succeed), HTTP outcomes are `INFO`. The URL is redacted here rather than by
     * the caller, so a new call site cannot leak a ticket; a timeout keeps the
     * exception class name in `error`, which is what makes "no response at all"
     * distinguishable from "answered 401".
     */
    fun record(
        method: String,
        url: String,
        outcome: String,
        mark: TimeSource.Monotonic.ValueTimeMark,
        requestId: String? = null,
        error: Throwable? = null,
    ) {
        val transportFailure = !outcome.startsWith("HTTP ")
        HermesLog.record(
            severity = if (transportFailure) LogSeverity.WARN else LogSeverity.INFO,
            component = COMPONENT,
            message = "$method ${LogRedaction.url(url)}",
            attributes =
                mapOf(
                    "outcome" to outcome,
                    "duration_ms" to mark.elapsedNow().inWholeMilliseconds.toString(),
                ),
            error = error,
            requestId = requestId,
        )
    }
}
