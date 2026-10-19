package com.m57.hermescontrol

/**
 * Build-time values the shared code needs but `:shared` cannot see, because
 * `BuildConfig` is generated per Android build.
 */
expect object BuildFlags {
    /** True when the build permits plaintext HTTP to a LAN gateway. */
    val allowCleartext: Boolean

    /** Human-readable version, e.g. `1.4.2`. */
    val versionName: String

    /** Monotonic build number. */
    val versionCode: Int

    /** True for debug builds. */
    val isDebug: Boolean

    /** Short commit the build came from; empty when unknown. */
    val gitSha: String
}
