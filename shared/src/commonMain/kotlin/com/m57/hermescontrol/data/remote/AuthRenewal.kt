package com.m57.hermescontrol.data.remote

import kotlin.concurrent.Volatile

/**
 * A renewable credential, installed by whoever owns one.
 *
 * `safeApiCall` lives in `shared` and must not know about the native sign-in (which is `ui`), so
 * the direction of the dependency is inverted here: the app installs a renewal, the network layer
 * asks whether one exists.
 *
 * Returning true means "the next attempt should succeed": the implementation is responsible for
 * updating whatever the transport reads its credential from. Returning false leaves the caller's
 * usual behaviour — the session is reported as expired.
 */
object AuthRenewal {
    @Volatile
    var renew: (suspend () -> Boolean)? = null

    /** True when a renewal is installed, i.e. the credential can survive a 401. */
    val isAvailable: Boolean get() = renew != null

    fun resetForTest() {
        renew = null
    }
}
