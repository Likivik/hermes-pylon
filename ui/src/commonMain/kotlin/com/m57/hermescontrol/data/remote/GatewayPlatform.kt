/**
 * The two platform seams behind [ApiClient].
 *
 * Everything else about the gateway REST surface is shared code; these two
 * declarations are the only per-target knowledge. Keeping them this small is
 * the point of the tranche: a target cannot drift into its own ApiClient, and
 * the auth rule itself ([gatewayBearerValue]) lives in shared code.
 */
package com.m57.hermescontrol.data.remote

import io.ktor.client.HttpClient

/** Which credentials a client should attach. */
internal sealed interface GatewayClientAuth {
    /** Read the platform session store live, on every request. */
    data object SessionCredentials : GatewayClientAuth

    /**
     * A fixed bearer for a throwaway client ([ApiClient.tempService]).
     * A blank [token] means "attach no Authorization header".
     */
    data class FixedBearer(
        val token: String,
    ) : GatewayClientAuth
}

/**
 * Where the live session points, and what it is allowed to send.
 *
 * Android: `AuthManager.endpointForBuild()`, with credentials read live from
 * `AuthManager` inside the client's interceptor (which is why this object does
 * not expose the token — a snapshot would break token refresh).
 * Desktop: the `hermes.gatewayUrl` system property.
 * Wasm: the page's own origin.
 */
internal expect object GatewayPlatform {
    /** Endpoint of the active session. */
    fun endpoint(): ServerEndpoint
}

/**
 * Builds the engine-bound client for one auth mode.
 *
 * Android overrides this with Ktor-over-OkHttp (`preconfigured` on the shared
 * `OkHttpProvider` client), keeping the persistent cookie jar, the
 * credential-boundary interceptors, `TokenRefreshAuthenticator` and debug-only
 * HTTP logging. Desktop/wasm return a plain Ktor client.
 */
internal expect fun createGatewayHttpClient(auth: GatewayClientAuth): HttpClient

/**
 * The one bearer-header rule, shared so no target can get it wrong.
 *
 * Gated profiles authenticate with the session cookie alone: a stale loopback
 * token sent alongside it makes the dashboard reject otherwise valid requests.
 * A blank token likewise means "no header" rather than an empty one.
 */
internal fun gatewayBearerValue(
    gated: Boolean,
    token: String?,
): String? = if (gated || token.isNullOrBlank()) null else "Bearer $token"
