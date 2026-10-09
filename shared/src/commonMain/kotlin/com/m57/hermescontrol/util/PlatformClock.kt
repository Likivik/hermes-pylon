package com.m57.hermescontrol.util

/**
 * Wall-clock milliseconds since the epoch.
 *
 * Multiplatform replacement for `System.currentTimeMillis()`, which is JVM-only
 * and therefore unavailable in commonMain. Callers that need a *test* clock
 * should keep injecting one; this is the production default.
 */
expect fun nowMillis(): Long
