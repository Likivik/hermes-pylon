package com.m57.hermescontrol.util

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Dispatcher for blocking I/O work (HTTP, disk).
 *
 * Android and desktop have a dedicated I/O pool; wasmJs has no blocking I/O and
 * no `Dispatchers.IO` at all, so it falls back to [kotlinx.coroutines.Dispatchers.Default].
 * Callers keep taking a [CoroutineDispatcher] parameter so tests can inject a
 * test dispatcher and drain the whole chain deterministically.
 */
expect val ioDispatcher: CoroutineDispatcher
