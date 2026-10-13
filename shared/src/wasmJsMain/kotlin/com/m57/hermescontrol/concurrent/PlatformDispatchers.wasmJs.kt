package com.m57.hermescontrol.concurrent

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * wasmJs: `Dispatchers.IO` does not exist here and there is nothing to block —
 * the browser has one thread, so work that the JVM would push onto the IO pool
 * simply runs on the default dispatcher.
 */
actual val ioDispatcher: CoroutineDispatcher = Dispatchers.Default
