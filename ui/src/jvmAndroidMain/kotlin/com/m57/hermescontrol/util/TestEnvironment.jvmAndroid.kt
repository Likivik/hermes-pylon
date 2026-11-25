@file:Suppress("ktlint:standard:filename")

package com.m57.hermescontrol.util

actual fun isRunningUnderTest(): Boolean =
    try {
        Class.forName("org.junit.Test")
        true
    } catch (_: ClassNotFoundException) {
        false
    }
