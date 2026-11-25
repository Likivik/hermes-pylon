package com.m57.hermescontrol.data.remote

import io.ktor.client.HttpClient
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
object GatewayConnection {
    /** A built client together with the gateway that drives it. */
    private class Gateway(
        val client: HttpClient,
        val api: HermesApi,
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

    /** The current [HermesApi] instance. Lazily created on first access. */
    val hermesApi: HermesApi
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
    ): HermesApi {
        val endpoint = ServerEndpoint.parseForBuild(baseUrl)
        return HermesApi(
            createGatewayHttpClient(GatewayClientAuth.FixedBearer(token)),
            endpoint.baseUrl,
        )
    }

    /**
     * How many times the gateway client has been built in this process.
     *
     * A request captures this number and compares it on return: if it changed, the client was rebuilt (endpoint or
     * profile changed) while the request was in flight, and the answer belongs to the old one. A number survives an
     * interface boundary; object identity does not, which is why fences used to reach for the singleton itself.
     */
    var generation: Long = 0L
        private set

    private fun buildGateway(): Gateway {
        val endpoint = GatewayPlatform.endpoint()
        val profile = selectedProfileId
        // The profile rides in the client, not in each call: one place appends it,
        // so "forgot the parameter" is not a failure mode.
        val client = createGatewayHttpClient(GatewayClientAuth.SessionCredentials, profile)
        generation++
        return Gateway(client, HermesApi(client, endpoint.baseUrl))
    }
}
