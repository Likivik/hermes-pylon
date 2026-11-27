package com.m57.hermescontrol.ui.authlogin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URLDecoder
import com.m57.hermescontrol.util.HermesLog

private const val TAG = "LoopbackListener"

/**
 * Loopback listener for the JVM and Android.
 *
 * Binds `127.0.0.1` explicitly rather than `ServerSocket(0)`: the default binds every interface,
 * which would expose the callback to the network and let another machine deliver a code.
 */
private class JvmLoopbackListener(
    private val timeoutMillis: Long,
) : LoopbackListener {
    private val server = ServerSocket(0, 1, InetAddress.getByName(LOOPBACK_ADDRESS))

    override val port: Int
        get() = server.localPort

    override suspend fun awaitCallback(expectedState: String): LoopbackOutcome =
        withContext(Dispatchers.IO) {
            val deadline = System.currentTimeMillis() + timeoutMillis
            try {
                while (true) {
                    val remaining = deadline - System.currentTimeMillis()
                    if (remaining <= 0) return@withContext LoopbackOutcome.TimedOut
                    server.soTimeout = remaining.toInt().coerceAtLeast(1)

                    val socket =
                        try {
                            server.accept()
                        } catch (_: SocketTimeoutException) {
                            return@withContext LoopbackOutcome.TimedOut
                        } catch (_: IOException) {
                            // close() was called while waiting: treat as a cancelled flow, not a hang.
                            return@withContext LoopbackOutcome.TimedOut
                        }

                    val outcome = socket.use { handle(it, expectedState) }
                    if (outcome != null) return@withContext outcome
                }
                @Suppress("UNREACHABLE_CODE")
                LoopbackOutcome.TimedOut
            } finally {
                close()
            }
        }

    /**
     * Null means "not this flow — keep waiting". Only a matching `state` can end the wait, and it
     * ends it whether or not a code came with it.
     */
    private fun handle(
        socket: Socket,
        expectedState: String,
    ): LoopbackOutcome? {
        socket.soTimeout = READ_TIMEOUT_MILLIS
        val requestLine =
            try {
                socket.getInputStream().bufferedReader().readLine()
            } catch (_: IOException) {
                return null
            } ?: return null

        val target = requestLine.split(' ').getOrNull(1) ?: return null
        val params = queryOf(target)

        if (params["state"] != expectedState) {
            respond(socket, 400, "State mismatch")
            return null
        }

        val code = params["code"]
        if (code.isNullOrEmpty()) {
            // The provider reports a refusal (user cancelled, consent denied) as ?error=...
            respond(socket, 400, "Sign-in was not completed")
            return LoopbackOutcome.Rejected(params["error"].orEmpty().ifEmpty { "missing code" })
        }

        respond(socket, 200, "Signed in. You can close this tab and return to Pylon.")
        return LoopbackOutcome.Received(LoopbackCallback(code = code, state = params["state"]))
    }

    private fun queryOf(target: String): Map<String, String> {
        val query = target.substringAfter('?', missingDelimiterValue = "")
        if (query.isEmpty()) return emptyMap()
        return query
            .split('&')
            .mapNotNull { pair ->
                val key = pair.substringBefore('=', "").ifEmpty { return@mapNotNull null }
                key to URLDecoder.decode(pair.substringAfter('=', ""), Charsets.UTF_8.name())
            }.toMap()
    }

    private fun respond(
        socket: Socket,
        status: Int,
        message: String,
    ) {
        val body = "<!doctype html><meta charset=utf-8><p>$message</p>"
        val response =
            "HTTP/1.1 $status ${if (status == 200) "OK" else "Bad Request"}\r\n" +
                "Content-Type: text/html; charset=utf-8\r\n" +
                "Content-Length: ${body.encodeToByteArray().size}\r\n" +
                "Connection: close\r\n\r\n" +
                body
        runCatching {
            socket.getOutputStream().apply {
                write(response.encodeToByteArray())
                flush()
            }
        }.onFailure { HermesLog.w(TAG, "the loopback callback could not be answered (${it::class.simpleName})") }
    }

    override fun close() {
        runCatching { server.close() }
                        .onFailure { HermesLog.d(TAG, "closing the loopback server failed (${it::class.simpleName})") }
    }

    private companion object {
        const val LOOPBACK_ADDRESS = "127.0.0.1"
        const val READ_TIMEOUT_MILLIS = 5_000
    }
}

actual fun openLoopbackListener(timeoutMillis: Long): LoopbackListener = JvmLoopbackListener(timeoutMillis)

/** A JVM or Android process can bind a socket on the loopback interface. */
actual val loopbackIsAvailable: Boolean = true
