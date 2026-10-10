package com.m57.hermescontrol.util

actual object PlatformLog {
    actual fun warn(
        tag: String,
        message: String,
    ) {
        System.err.println("W/$tag: $message")
    }
}
