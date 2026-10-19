@file:Suppress("ktlint:standard:filename")

package com.m57.hermescontrol.util

actual fun <T> synchronizedOn(
    lock: Any,
    block: () -> T,
): T = synchronized(lock, block)
