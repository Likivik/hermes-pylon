package com.m57.hermescontrol.data.remote

import kotlinx.io.IOException

/** JVM/Android host-level failures (DNS, TLS handshake) will not succeed on retry. */
actual fun isRetryable(e: IOException): Boolean =
    when (e) {
        is java.net.UnknownHostException -> false
        is javax.net.ssl.SSLException -> false
        is java.net.ConnectException -> true
        is java.net.SocketTimeoutException -> true
        else -> true
    }
