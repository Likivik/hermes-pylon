package com.m57.hermescontrol.data.security

import kotlinx.atomicfu.locks.SynchronizedObject

actual fun holdsMonitor(lock: SynchronizedObject): Boolean = Thread.holdsLock(lock)
