package com.m57.hermescontrol.concurrent

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Dispatcher for work that blocks the calling thread.
 *
 * `Dispatchers.IO` is JVM-only, so the name lives here instead of at the call
 * sites. JVM and Android keep the real thing — moving them onto
 * `Dispatchers.Default` would silently resize the pool for socket work — and a
 * threadless target substitutes a dispatcher that exists there.
 */
expect val ioDispatcher: CoroutineDispatcher
