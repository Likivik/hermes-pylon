package com.m57.hermescontrol.data.remote

/**
 * Entry point to the gateway REST surface.
 *
 * [HermesGatewayApi] is fully multiplatform; only the *construction* of its
 * `HttpClient` differs per target (Android keeps OkHttp, cookies, TLS pinning and
 * token-refresh; desktop/wasm build a plain Ktor client). Shared code depends on
 * this expect declaration, whose actuals live in the platform source sets.
 */
expect object ApiClient {
    /** The current [HermesGatewayApi] instance, created lazily on first access. */
    val hermesApi: HermesGatewayApi

    fun tempService(
        baseUrl: String,
        token: String,
    ): HermesGatewayApi

    fun rebuild()

    /**
     * POSTs a raw JSON body to [path] and returns the HTTP status code.
     *
     * This exists for calls that must not go through the typed [HermesGatewayApi]
     * surface — password login runs before a session exists and needs its own short
     * timeouts. It still uses the target's real client, so on Android the request
     * travels the same OkHttp stack (cookie jar, TLS pinning) as everything else.
     */
    suspend fun postJson(
        path: String,
        jsonBody: String,
        timeoutMillis: Long = 10_000,
    ): Int
}
