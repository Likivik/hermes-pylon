package com.m57.hermescontrol.data.security

import kotlinx.atomicfu.locks.SynchronizedObject

/** Single-threaded: nothing else can hold the monitor while this code runs. */
actual fun holdsMonitor(lock: SynchronizedObject): Boolean = true
