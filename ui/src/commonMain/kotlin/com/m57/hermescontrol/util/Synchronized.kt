package com.m57.hermescontrol.util

/**
 * Mutual exclusion that every target can express.
 *
 * `kotlin.synchronized` is JVM-only, but wasmJs has a single thread, so a direct
 * call is already atomic there — the lock exists to keep one shared source file
 * honest across targets rather than to serialise anything on the web.
 */
expect fun <T> synchronizedOn(
    lock: Any,
    block: () -> T,
): T
