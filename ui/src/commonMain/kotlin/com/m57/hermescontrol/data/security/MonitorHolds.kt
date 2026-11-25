package com.m57.hermescontrol.data.security

import kotlinx.atomicfu.locks.SynchronizedObject

/**
 * Whether the calling thread currently owns [lock].
 *
 * Used for internal invariant checks in `AuthManager`; JVM asks the VM, targets
 * that are single-threaded answer in the affirmative by definition.
 */
expect fun holdsMonitor(lock: SynchronizedObject): Boolean
