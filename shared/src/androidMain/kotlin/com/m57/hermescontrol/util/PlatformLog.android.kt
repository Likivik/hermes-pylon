package com.m57.hermescontrol.util

actual object PlatformLog {
    actual fun warn(
        tag: String,
        message: String,
    ) {
        android.util.Log.w(tag, message)
    }
}
