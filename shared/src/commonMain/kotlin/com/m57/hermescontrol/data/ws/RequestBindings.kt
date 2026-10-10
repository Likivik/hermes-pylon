package com.m57.hermescontrol.data.ws

// Routing identities for gateway requests, shared across platforms.
//
// These are pure value types: they carry WHO a request belongs to (profile,
// runtime session, connection generation) so a reply can be matched to the
// exact request that asked for it. They deliberately exclude ConnectionBinding
// (app-local, holds the live okhttp3 socket) — the socket is a transport detail
// the console client will supply from its own engine.

/**
 * Connection status for the WebSocket client.
 */
enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    NO_NETWORK,
    AUTH_EXPIRED,
}

/**
 * A [WsEvent] annotated with the connection it arrived on, so consumers can
 * discard events from a superseded socket.
 *
 * Public (not internal) because the WebSocket client that builds these lives in
 * :app while the type now lives here.
 */
data class SourcedWsEvent(
    val event: WsEvent,
    val profileId: String?,
    val connectionGeneration: Int,
    val storedSessionId: String? = null,
)

data class PrivilegedRequestBinding(
    val requestId: String,
    val runtimeSessionId: String,
    val profileId: String,
    val connectionGeneration: Int,
)

data class ServerRequestBinding(
    val requestId: String,
    val runtimeSessionId: String,
    val profileId: String,
    val connectionGeneration: Int,
)
