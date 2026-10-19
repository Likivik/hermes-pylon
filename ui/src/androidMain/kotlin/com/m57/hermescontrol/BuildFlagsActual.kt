@file:Suppress("ktlint:standard:filename")

package com.m57.hermescontrol

/** Android reads the generated `BuildConfig`. */
actual object BuildFlags {
    actual val allowCleartext: Boolean = BuildConfig.ALLOW_CLEARTEXT
    actual val versionName: String = BuildConfig.VERSION_NAME
    actual val versionCode: Int = BuildConfig.VERSION_CODE
    actual val isDebug: Boolean = BuildConfig.DEBUG
    actual val gitSha: String = BuildConfig.GIT_SHA
}
