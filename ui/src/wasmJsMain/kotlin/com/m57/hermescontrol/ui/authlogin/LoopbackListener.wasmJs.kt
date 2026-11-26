package com.m57.hermescontrol.ui.authlogin

/**
 * The browser cannot open a listening socket, so there is no loopback redirect to receive.
 *
 * This is not a gap to fill later: it is why the web build signs in with the gateway's cookie
 * flow. Reaching this function means the UI offered a flow it cannot complete, which is a bug in
 * the availability gate rather than something to recover from here.
 */
private class UnsupportedLoopbackListener : LoopbackListener {
    override val port: Int
        get() = throw UnsupportedOperationException("a browser cannot listen on loopback")

    override suspend fun awaitCallback(expectedState: String): LoopbackOutcome =
        throw UnsupportedOperationException("a browser cannot listen on loopback")

    override fun close() = Unit
}

actual fun openLoopbackListener(timeoutMillis: Long): LoopbackListener = UnsupportedLoopbackListener()
