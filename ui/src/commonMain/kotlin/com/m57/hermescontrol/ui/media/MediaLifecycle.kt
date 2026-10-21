package com.m57.hermescontrol.ui.media

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/*
 * Coroutine scaffolding for media that is initialized off the main thread and
 * must be closed exactly once, even when composition is cancelled mid-flight.
 * Pure coroutines: no platform type is named, so these live here rather than in
 * each target.
 */

suspend fun <T> initializeSecureMedia(
    dispatcher: CoroutineDispatcher,
    initializer: () -> T,
): T = withContext(dispatcher) { initializer() }

suspend fun <T : Any> initializeAndOwnSecureMedia(
    dispatcher: CoroutineDispatcher,
    initializer: () -> T,
    publish: (T) -> Unit,
    close: (T) -> Unit,
) {
    var initialized: T? = null
    try {
        initialized = initializeSecureMedia(dispatcher, initializer)
        currentCoroutineContext().ensureActive()
        publish(initialized)
        awaitCancellation()
    } finally {
        initialized?.let(close)
    }
}

suspend fun monitorSecureMediaBoundary(
    dispatcher: CoroutineDispatcher,
    pollIntervalMillis: Long,
    isCurrent: () -> Boolean,
    onCurrent: () -> Unit,
    onStale: () -> Unit,
) {
    while (true) {
        if (!withContext(dispatcher) { isCurrent() }) {
            onStale()
            return
        }
        onCurrent()
        delay(pollIntervalMillis)
    }
}
