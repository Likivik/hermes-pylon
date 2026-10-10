package com.m57.hermescontrol.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json

/**
 * Transport policy for a gateway [HttpClient], mirrored from the values baked
 * into `OkHttpProvider` so the two stacks behave the same during migration.
 *
 * [OkHttpProvider] sets connect 15s / read 30s / write 15s on every client, 5s
 * for probes, and an unbounded read timeout on the WebSocket client.
 */
data class HermesHttpConfig(
    val connectTimeoutMillis: Long = 15_000,
    val requestTimeoutMillis: Long = 30_000,
    val socketTimeoutMillis: Long = 30_000,
    /**
     * Connection-level retries. Deliberately mirrors OkHttp's
     * `retryOnConnectionFailure(true)` and NOT a 5xx/TIMEOUT retry loop: the app
     * already backs off for 5xx, 429 and timeouts in `safeApiCall`, and retrying
     * those here too would multiply every retry by this count.
     */
    val maxRetries: Int = 2,
    val logLevel: LogLevel = LogLevel.NONE,
) {
    companion object {
        val Default = HermesHttpConfig()

        /** Probe / ticket-minting client: short timeouts, like OkHttpProvider.probe. */
        val Probe =
            HermesHttpConfig(
                connectTimeoutMillis = 5_000,
                requestTimeoutMillis = 5_000,
                socketTimeoutMillis = 5_000,
            )

        /** Streaming client (WebSocket, long poll): no request timeout. */
        fun streaming() =
            Default.copy(
                requestTimeoutMillis = Long.MAX_VALUE,
                socketTimeoutMillis = Long.MAX_VALUE,
            )
    }
}

/**
 * Builds a gateway client on the platform's default Ktor engine.
 *
 * Not yet used by :app — `HermesApiService` and `OkHttpProvider` still own the
 * Android transport, and its tests pin Retrofit's types. This is the floor the
 * console target and future platforms consume.
 */
fun createHermesHttpClient(config: HermesHttpConfig = HermesHttpConfig.Default): HttpClient =
    HttpClient { installHermesPlugins(config) }

/** Variant for tests ([io.ktor.client.engine.mock.MockEngine]) and for engines supplied by DI. */
fun createHermesHttpClient(
    engine: HttpClientEngine,
    config: HermesHttpConfig = HermesHttpConfig.Default,
): HttpClient = HttpClient(engine) { installHermesPlugins(config) }

/**
 * Installs the shared plugin set.
 *
 * Two deliberate omissions/orderings:
 * - `HttpRequestRetry` is installed *before* `HttpTimeout`, so retries can cover
 *   timeouts (Ktor documents that order requirement).
 * - `HttpCookies` is NOT installed. On Android the OkHttp engine owns cookies
 *   through OkHttpProvider's `PersistentCookieJar`, and Ktor's store would be a
 *   second, diverging jar.
 */
fun HttpClientConfig<*>.installHermesPlugins(config: HermesHttpConfig) {
    // Callers inspect status codes themselves (the Retrofit side returns
    // Response<T> and never throws), so a 4xx/5xx is a value, not an exception.
    expectSuccess = false

    install(HttpRequestRetry) {
        retryOnException(maxRetries = config.maxRetries)
        exponentialDelay()
    }

    install(HttpTimeout) {
        connectTimeoutMillis = config.connectTimeoutMillis
        requestTimeoutMillis = config.requestTimeoutMillis
        socketTimeoutMillis = config.socketTimeoutMillis
    }

    install(ContentNegotiation) {
        // Byte-identical serialization with the Retrofit converter.
        json(HermesJson.instance)
    }

    install(Logging) {
        level = config.logLevel
        // Never log credentials, even at LogLevel.ALL.
        sanitizeHeader { header ->
            header.equals(HttpHeaders.Authorization, ignoreCase = true) ||
                header.equals(HttpHeaders.Cookie, ignoreCase = true) ||
                header.equals(HttpHeaders.SetCookie, ignoreCase = true)
        }
    }
}
