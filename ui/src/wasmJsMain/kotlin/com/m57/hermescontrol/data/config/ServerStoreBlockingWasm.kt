package com.m57.hermescontrol.data.config

import kotlinx.coroutines.flow.Flow

/** The browser has no blocking primitive: start on the fallback and let the store fill in. */
internal actual fun <T> firstBlocking(
    flow: Flow<T>,
    fallback: T,
): T = fallback
