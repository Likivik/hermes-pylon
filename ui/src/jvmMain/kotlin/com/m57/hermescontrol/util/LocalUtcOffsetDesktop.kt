@file:Suppress("ktlint:standard:filename")

package com.m57.hermescontrol.util

import java.util.TimeZone

actual fun localUtcOffsetMinutes(): Int = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000
