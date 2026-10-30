package com.m57.hermescontrol.data.remote

import com.m57.hermescontrol.data.local.AuthManager
import com.m57.hermescontrol.util.ioDispatcher
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.io.readByteArray

object DashboardSessionTokenRefresher {
    // Probe client: no jar, no retry, redirects off (see [fetch]).
    private val probeClient: HttpClient by lazy { createHermesHttpClient(HermesHttpConfig.Probe) }

    private val tokenPattern = Regex("""__HERMES_SESSION_TOKEN__\s*=\s*"([^"]+)"""")

    // Synchronized so concurrent 401s / reconnect storms don't each fire a
    // redundant GET at the dashboard SPA (thundering-herd guard).
    @Synchronized
    fun refresh(): String? =
        try {
            val boundary = AuthManager.credentialBoundary()
            val token = fetch(boundary.endpoint.baseUrl.toString()) ?: return null
            token.takeIf { AuthManager.commitRefreshedToken(boundary, it) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }

    /**
     * Read the dashboard SPA and pull the session token out of its inline script.
     *
     * Redirects are off ([HermesHttpConfig.Probe]): a 302 here means the URL is
     * the web UI, not the gateway, and following it would fetch a login page and
     * report "no token" for the wrong reason.
     */
    fun fetch(
        baseUrl: String,
        client: HttpClient = probeClient,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher = ioDispatcher,
    ): String? =
        try {
            runBlocking(dispatcher) {
                val response = client.get(baseUrl)
                if (!response.status.isSuccess()) return@runBlocking null
                val bytes =
                    response.bodyAsChannel()
                        .readRemaining(MAX_BODY_BYTES)
                        .readByteArray()
                tokenPattern
                    .find(bytes.decodeToString())
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.takeIf { it.isNotBlank() }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }

    private const val MAX_BODY_BYTES: Long = 64 * 1024
}
