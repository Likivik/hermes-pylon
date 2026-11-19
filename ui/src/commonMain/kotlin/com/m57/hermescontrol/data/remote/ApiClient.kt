package com.m57.hermescontrol.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Entry point to the gateway REST surface, shared by every target.
 *
 * There is one implementation. The only things a platform supplies are the
 * session endpoint ([GatewayPlatform.endpoint]) and the engine-bound
 * [HttpClient] ([createGatewayHttpClient]) — never a second copy of this file.
 * Android keeps its OkHttp stack (shared connection pool, persistent cookie jar,
 * TLS pinning, token refresh) by building Ktor *on top of* it; desktop and wasm
 * build a plain Ktor client.
 */
object ApiClient {
    /** A built client together with the gateway that drives it. */
    private class Gateway(
        val client: HttpClient,
        val api: HermesGatewayApi,
    )

    /**
     * Current gateway generation.
     *
     * A [Lazy] rather than a nullable field so concurrent first access builds
     * exactly one client per generation (`lazy` is synchronized on JVM).
     * [rebuild] swaps in a fresh lazy instead of mutating the old one.
     */
    private var current: Lazy<Gateway> = lazy { buildGateway() }

    /**
     * The profile every request is scoped to, or null before the app has learned it.
     *
     * Part of the client generation on purpose: a request cannot forget the profile,
     * because the parameter is appended by the client itself. The gateway ignores it on
     * global routes and scopes data on the rest (`?profile=`).
     */
    private val selection = MutableStateFlow<String?>(null)

    /** Read from network threads, so it is a `StateFlow`, not a plain var. */
    val selectedProfileId: String?
        get() = selection.value

    /**
     * The selection itself, replaying its current value.
     *
     * Screens converge on this state instead of reacting to one-shot events: a change that
     * happened before a collector started is still observable.
     */
    val profileFlow: StateFlow<String?> = selection

    /**
     * Scope every subsequent request to [profile].
     *
     * Rebuilds the generation, so responses from the previous profile can never be
     * delivered into the new one.
     */
    fun setSelectedProfile(profile: String?) {
        val next = profile?.takeIf { it.isNotBlank() }
        if (next == selection.value) return
        selection.value = next
        resolvedProfile = next
        rebuild()
    }

    /**
     * Forget the selection (used when the gateway changes: a profile name that exists on
     * one gateway need not exist on the next).
     */
    fun clearSelectedProfile() = setSelectedProfile(null)

    private var resolvedProfile: String? = null

    /**
     * The profile this generation should scope to, resolved once.
     *
     * One request per mutation would be wasteful, and a name cached across a rebuild would
     * be wrong — so the memo is cleared by [rebuild] through [setSelectedProfile].
     */
    suspend fun currentProfileName(): String? {
        selectedProfileId?.let { return it }
        resolvedProfile?.let { return it }
        val resolved = hermesApi.activeProfileName() ?: return null
        resolvedProfile = resolved
        setSelectedProfile(resolved)
        return resolved
    }

    /** The current [HermesGatewayApi] instance. Lazily created on first access. */
    val hermesApi: HermesGatewayApi
        get() = current.value.api

    /**
     * Force-rebuild the gateway client (e.g. after a settings or profile change).
     *
     * Deliberately does NOT close the outgoing [HttpClient]: on Android it is
     * built on the app's shared `OkHttpProvider` client via `preconfigured`, and
     * Ktor's OkHttp engine `close()` shuts down that client's dispatcher
     * executor and evicts its connection pool. Dropping the reference is enough;
     * the underlying stack is owned by the platform and also serves the
     * WebSocket client.
     */
    fun rebuild() {
        current = lazy { buildGateway() }
    }

    /**
     * Creates a standalone, temporary gateway without modifying the global
     * instance — password login and token probing run before a session exists.
     */
    fun tempService(
        baseUrl: String,
        token: String,
    ): HermesGatewayApi {
        val endpoint = ServerEndpoint.parseForBuild(baseUrl)
        return HermesGatewayApi(
            createGatewayHttpClient(GatewayClientAuth.FixedBearer(token)),
            endpoint.baseUrl,
        )
    }

    /**
     * POSTs a raw JSON body to [path] and returns the HTTP status code.
     *
     * This exists for calls that must not go through the typed [HermesGatewayApi]
     * surface — password login runs before a session exists and needs its own
     * short timeouts. It travels the session client, so on Android the request
     * keeps the shared OkHttp stack (cookie jar, TLS pinning, token refresh).
     */
    suspend fun postJson(
        path: String,
        jsonBody: String,
        timeoutMillis: Long = 10_000,
    ): Int {
        val gateway = current.value
        // Re-read the endpoint per call: the user can move the session while a
        // client generation is still alive.
        val baseUrl = GatewayPlatform.endpoint().baseUrl.toString()
        val response =
            gateway.client.request(baseUrl.trimEnd('/') + "/" + path.trimStart('/')) {
                method = HttpMethod.Post
                contentType(ContentType.Application.Json)
                setBody(jsonBody)
                timeout {
                    requestTimeoutMillis = timeoutMillis
                    connectTimeoutMillis = timeoutMillis
                }
            }
        return response.status.value
    }

    private fun buildGateway(): Gateway {
        val endpoint = GatewayPlatform.endpoint()
        val profile = selectedProfileId
        // The profile rides in the client, not in each call: one place appends it,
        // so "forgot the parameter" is not a failure mode.
        val client = createGatewayHttpClient(GatewayClientAuth.SessionCredentials, profile)
        return Gateway(client, HermesGatewayApi(client, endpoint.baseUrl))
    }
}
