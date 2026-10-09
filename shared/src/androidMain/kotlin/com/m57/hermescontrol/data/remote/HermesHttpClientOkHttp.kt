package com.m57.hermescontrol.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.OkHttpClient

/**
 * Builds a gateway client on a caller-owned [OkHttpClient].
 *
 * Ktor drives the same connection pool, cookie jar, interceptors and
 * authenticator Retrofit used to drive, so session behaviour is preserved
 * exactly while the REST surface moves to the multiplatform floor.
 */
fun createHermesHttpClient(
    okHttpClient: OkHttpClient,
    config: HermesHttpConfig = HermesHttpConfig.Default,
): HttpClient =
    HttpClient(OkHttp) {
        engine { preconfigured = okHttpClient }
        installHermesPlugins(config)
    }
