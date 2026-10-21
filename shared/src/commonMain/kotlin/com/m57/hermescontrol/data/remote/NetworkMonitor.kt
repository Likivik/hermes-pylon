package com.m57.hermescontrol.data.remote

import kotlinx.coroutines.flow.StateFlow

/**
 * Network reachability, as `:shared` code consumes it.
 *
 * `:shared` cannot name `ConnectivityManager`, so the Android implementation
 * ([AndroidNetworkMonitor] in `:app`) owns platform registration and this seam
 * carries only what consumers read. A console or test target supplies its own.
 */
interface NetworkMonitor {
    /** Optimistically `true` until the platform reports otherwise. */
    val isConnected: StateFlow<Boolean>
}
