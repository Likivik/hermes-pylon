package com.m57.hermescontrol.concurrent

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Android: the real IO dispatcher. */
actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
