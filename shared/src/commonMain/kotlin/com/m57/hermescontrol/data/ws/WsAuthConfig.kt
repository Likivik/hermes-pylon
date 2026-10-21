package com.m57.hermescontrol.data.ws

import com.m57.hermescontrol.data.remote.ServerEndpoint

/**
 * The auth and endpoint facts the WS client needs.
 *
 * A narrow slice of the app's `AuthManager` — 716 lines and Android-only — of
 * which the client only ever asks these six questions. Carrying the seam lets
 * `:shared` own the client without owning the singleton: the app installs
 * `AuthManager` as the implementation, a console target supplies its own.
 */
interface WsAuthConfig {
    /** Profile whose connection the client manages; null when none is selected. */
    fun getSelectedProfileId(): String?

    /** True when the dashboard gates WS access behind a single-use ticket. */
    fun isGatedMode(): Boolean

    /** Whether the client reconnects on its own after a drop. */
    fun isAutoReconnect(): Boolean

    /** WebSocket endpoint for the selected profile. */
    fun wsUrl(): String

    /** [wsUrl] with a handshake-local [credential] attached. */
    fun wsUrlWithCredential(
        credential: String,
        authParameter: String = "ticket",
    ): String

    /** Base endpoint for this build; used to mint a gated-mode ticket. */
    fun endpointForBuild(): ServerEndpoint
}

/**
 * Placeholder until a real config is installed.
 *
 * The endpoint accessors fail loudly: reaching them means the app never
 * installed its `AuthManager`, and an empty URL would otherwise surface much
 * later as an opaque handshake failure.
 */
object UnconfiguredAuth : WsAuthConfig {
    override fun getSelectedProfileId(): String? = null

    override fun isGatedMode(): Boolean = false

    override fun isAutoReconnect(): Boolean = false

    override fun wsUrl(): String = error("WsAuthConfig not installed")

    override fun wsUrlWithCredential(
        credential: String,
        authParameter: String,
    ): String = error("WsAuthConfig not installed")

    override fun endpointForBuild(): ServerEndpoint = error("WsAuthConfig not installed")
}
