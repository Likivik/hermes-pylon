package com.m57.hermescontrol.util

/**
 * Warn-level logging on every target.
 *
 * Shared code cannot call `android.util.Log` or `println` directly: the first is
 * Android-only and the second does not exist on every target. Routing through an
 * expect/actual keeps the shared half honest about what the platform provides,
 * exactly as [nowMillis] does for time.
 */
expect object PlatformLog {
    fun warn(
        tag: String,
        message: String,
    )
}
