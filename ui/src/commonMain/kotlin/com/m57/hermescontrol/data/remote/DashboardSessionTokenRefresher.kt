package com.m57.hermescontrol.data.remote

import com.m57.hermescontrol.data.local.AuthManager
import com.m57.hermescontrol.util.ioDispatcher
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.readByteArray

/**
 * Recovers the dashboard session token from the SPA after the gateway drops it.
 *
 * Portable on purpose: the previous version used `@Synchronized` and
 * `runBlocking`, neither of which exists on wasmJs, which kept the whole
 * transport layer on Android. Serialisation is now a [Mutex] and the blocking
 * call is a `suspend` function — callers are already suspending (Ktor
 * `intercept`, view-model scopes).
 */
object DashboardSessionTokenRefresher {
    // Probe client: no jar, no retry, redirects off (see [fetch]).
    private val probeClient: HttpClient by lazy { createHermesHttpClient(HermesHttpConfig.Probe) }

    private val tokenPattern = Regex("""__HERMES_SESSION_TOKEN__\s*=\s*"([^"]+)"""")

    // Serialised so concurrent 401s / reconnect storms don't each fire a
    // redundant GET at the dashboard SPA (thundering-herd guard).
    private val refreshMutex = Mutex()

    suspend fun refresh(): String? =
        refreshMutex.withLock {
            try {
                val boundary = AuthManager.credentialBoundary()
                val token = fetch(boundary.endpoint.baseUrl.toString()) ?: return@withLock null
                token.takeIf { AuthManager.commitRefreshedToken(boundary, it) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }

    /**
     * Read the dashboard SPA and pull the session token out of its inline script.
     *
     * Redirects are off ([HermesHttpConfig.Probe]): a 302 here means the URL is
     * the web UI, not the gateway, and following it would fetch a login page and
     * report "no token" for the wrong reason.
     */
    suspend fun fetch(
        baseUrl: String,
        client: HttpClient = probeClient,
        dispatcher: CoroutineDispatcher = ioDispatcher,
    ): String? =
        try {
            withContext(dispatcher) {
                val response = client.get(baseUrl)
                if (!response.status.isSuccess()) return@withContext null
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
