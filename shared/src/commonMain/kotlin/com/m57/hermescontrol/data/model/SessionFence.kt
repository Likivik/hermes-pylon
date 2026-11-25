package com.m57.hermescontrol.data.model

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Identity of one mutation attempt, as the gateway requires it.
 *
 * The gateway never commits an unfenced write: [requestId] makes the attempt idempotent
 * and replayable, [revision] pins it to the state the user saw, [generation] to the runtime
 * that produced it. A stale pair is a *conflict*, not a silent overwrite.
 */
data class SessionFence(
    val requestId: String,
    val revision: Int?,
    val generation: Int?,
) {
    companion object {
        /** A fence for one attempt against [row]; the row supplies revision and generation. */
        @OptIn(ExperimentalUuidApi::class)
        fun forRow(row: SessionInfo?): SessionFence =
            SessionFence(
                requestId = Uuid.random().toString(),
                revision = row?.runtimeRevision,
                generation = row?.runtimeGeneration,
            )
    }
}
