package com.m57.hermescontrol.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * wasmJs has no blocking I/O and no `Dispatchers.IO`; JS interop is event-loop
 * based, so the default pool is the correct choice.
 */
actual val ioDispatcher: CoroutineDispatcher
    get() = Dispatchers.Default
