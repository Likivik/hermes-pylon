package com.m57.hermescontrol.data.config

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

internal actual fun <T> firstBlocking(
    flow: Flow<T>,
    fallback: T,
// inventory: intentional-ignore — absence of a stored value is not a failure
): T = runCatching { runBlocking { flow.first() } }.getOrDefault(fallback)
