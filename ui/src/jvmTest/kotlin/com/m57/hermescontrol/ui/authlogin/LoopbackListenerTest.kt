package com.m57.hermescontrol.ui.authlogin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/**
 * These use a real socket rather than a fake transport.
 *
 * The failures worth catching here are all in the network stack — a listener bound to the wrong
 * interface, an `accept()` that never returns, a port left open after success, a request line parsed
 * at the wrong offset. A mock would agree with the implementation and hide every one of them.
 */
class LoopbackListenerTest {
    private fun fetch(
        port: Int,
        path: String,
    ): Int {
        val connection = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
        connection.connectTimeout = 2_000
        connection.readTimeout = 2_000
        return try {
            connection.responseCode
        } finally {
            connection.disconnect()
        }
    }

    @Test
    fun receivesTheCodeAndState() =
        runBlocking {
            val listener = openLoopbackListener(timeoutMillis = 5_000)
            val result = async(Dispatchers.IO) { listener.awaitCallback("state-1") }

            val status = fetch(listener.port, "/callback?code=abc123&state=state-1")
            val outcome = result.await()

            assertEquals(200, status)
            val callback = assertIs<LoopbackOutcome.Received>(outcome).callback
            assertEquals("abc123", callback.code)
            assertEquals("state-1", callback.state)
        }

    /**
     * A redirect from another flow must neither abort this one nor be mistaken for it. The wrong
     * state is answered and the listener keeps waiting for the right one.
     */
    @Test
    fun aMismatchedStateIsRefusedAndTheWaitContinues() =
        runBlocking {
            val listener = openLoopbackListener(timeoutMillis = 5_000)
            val result = async(Dispatchers.IO) { listener.awaitCallback("expected") }

            val wrongStatus = fetch(listener.port, "/callback?code=stolen&state=other")
            val rightStatus = fetch(listener.port, "/callback?code=mine&state=expected")
            val outcome = result.await()

            assertEquals(400, wrongStatus)
            assertEquals(200, rightStatus)
            assertEquals("mine", assertIs<LoopbackOutcome.Received>(outcome).callback.code)
        }

    /** A cancelled consent screen arrives as `?error=`, not as a code. */
    @Test
    fun aProviderErrorIsReportedAsRejected() =
        runBlocking {
            val listener = openLoopbackListener(timeoutMillis = 5_000)
            val result = async(Dispatchers.IO) { listener.awaitCallback("s") }

            fetch(listener.port, "/callback?error=access_denied&state=s")
            val outcome = result.await()

            assertEquals("access_denied", assertIs<LoopbackOutcome.Rejected>(outcome).reason)
        }

    /**
     * Without a working timeout the caller waits forever on a closed tab, holding the port and the
     * coroutine. The elapsed bound also proves the wait is bounded by the deadline, not by luck.
     */
    @Test
    fun timesOutWithoutABrowserCallback() =
        runBlocking {
            val listener = openLoopbackListener(timeoutMillis = 400)

            val elapsed = measureTimeMillis { assertIs<LoopbackOutcome.TimedOut>(listener.awaitCallback("s")) }

            assertEquals(true, elapsed < 5_000, "expected a prompt timeout, took ${elapsed}ms")
        }

    /** The port must be released on success: a listener still bound after the flow is a leak. */
    @Test
    fun thePortIsClosedOnceTheCallbackArrives() {
        runBlocking {
            val listener = openLoopbackListener(timeoutMillis = 5_000)
            val port = listener.port
            val result = async(Dispatchers.IO) { listener.awaitCallback("s") }

            fetch(port, "/callback?code=c&state=s")
            result.await()

            assertFailsWith<ConnectException> {
                Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 500) }
            }
        }
    }
}
