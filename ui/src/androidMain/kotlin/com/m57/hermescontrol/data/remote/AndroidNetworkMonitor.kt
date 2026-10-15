package com.m57.hermescontrol.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android [NetworkMonitor]: reachability from [ConnectivityManager].
 *
 * `:shared` owns the [NetworkMonitor] seam because it cannot name a platform
 * type; this object holds the only Android-specific part.
 */
object AndroidNetworkMonitor : NetworkMonitor {
    private val _isConnected = MutableStateFlow(true) // optimistic default

    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    fun init(context: Context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        _isConnected.value = cm.activeNetwork != null
        cm.registerDefaultNetworkCallback(
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _isConnected.value = true
                }

                override fun onLost(network: Network) {
                    _isConnected.value = false
                }
            },
        )
    }
}
