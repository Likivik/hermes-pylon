package com.m57.hermescontrol.ui.authlogin

/**
 * The browser cannot open a listening socket, so there is no loopback redirect to receive.
 *
 * This is not a gap to fill later: it is why the web build signs in with the gateway's cookie
 * flow. [NativeSignIn] turns this into `Unsupported`, which the availability gate should have
 * prevented anyway.
 */
actual fun openLoopbackListener(timeoutMillis: Long): LoopbackListener =
    throw UnsupportedOperationException("a browser cannot listen on loopback")
