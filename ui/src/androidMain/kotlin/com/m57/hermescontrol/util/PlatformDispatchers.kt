package com.m57.hermescontrol.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

// Resolved per access, not captured once: unit tests stub `Dispatchers.IO` with
// mockkStatic, and an eagerly-stored val would silently escape that stub and run
// real IO threads the test scheduler cannot await.
actual val ioDispatcher: CoroutineDispatcher
    get() = Dispatchers.IO
