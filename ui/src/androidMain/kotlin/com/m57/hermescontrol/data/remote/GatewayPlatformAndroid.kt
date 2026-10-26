// Modified from Hy4ri/hermes-mobile for this fork; see NOTICE.

package com.m57.hermescontrol.data.remote

import com.m57.hermescontrol.BuildConfig
import com.m57.hermescontrol.data.local.AuthManager
import io.ktor.client.HttpClient
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

/**
 * Android side of the [ApiClient] seam.
 *
 * No second HTTP stack is created: the gateway client is built *on top of* the
 * app's `OkHttpProvider.base` via Ktor's `preconfigured`, so OkHttp keeps owning
 * the connection pool, the persistent cookie jar, TLS pinning,
 * [TokenRefreshAuthenticator] and the credential-boundary interceptors, and
 * Ktor drives the same objects.
 */
internal actual object GatewayPlatform {
    actual fun endpoint(): ServerEndpoint = AuthManager.endpointForBuild()
}

internal actual fun createGatewayHttpClient(auth: GatewayClientAuth): HttpClient =
    when (auth) {
        GatewayClientAuth.SessionCredentials -> createHermesHttpClient(sessionOkHttpClient())
        is GatewayClientAuth.FixedBearer -> createHermesHttpClient(tempOkHttpClient(auth.token))
    }

/**
 * The app's session client.
 *
 * The bearer rule itself is shared ([gatewayBearerValue]); only the *reading* of
 * credentials is Android's, and it happens per request so a token refreshed
 * mid-flight is used by the retry.
 */
private fun sessionOkHttpClient(): OkHttpClient {
    val logging =
        HttpLoggingInterceptor().apply {
            level =
                if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BASIC
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
        }

    val authInterceptor =
        Interceptor { chain ->
            val request = chain.request()
            // Loopback mode uses Bearer auth. Gated profiles rely exclusively on
            // the session cookie attached by the shared CookieJar; adding a stale
            // token alongside it causes the dashboard to reject otherwise valid
            // REST requests.
            val bearer = gatewayBearerValue(AuthManager.isGatedMode(), AuthManager.getToken())
            if (bearer == null) {
                chain.proceed(request)
            } else {
                chain.proceed(
                    request
                        .newBuilder()
                        .addHeader("Authorization", bearer)
                        .build(),
                )
            }
        }

    return OkHttpProvider
        .base
        .newBuilder()
        .addInterceptor(authInterceptor)
        // A local connection ID is not a server management profile.
        // Preserve explicit request scopes; never infer one here.
        .addInterceptor(logging)
        .authenticator(TokenRefreshAuthenticator)
        .build()
}

/**
 * Throwaway client for [ApiClient.tempService] — a fixed bearer, no logging and
 * no global state touched. `newBuilder()` retains the base client's cookie jar,
 * TLS pinning and authenticator while adding the caller's token.
 */
private fun tempOkHttpClient(token: String): OkHttpClient {
    val tempAuthInterceptor =
        Interceptor { chain ->
            val bearer = gatewayBearerValue(gated = false, token = token)
            if (bearer == null) {
                chain.proceed(chain.request())
            } else {
                chain.proceed(
                    chain
                        .request()
                        .newBuilder()
                        .addHeader("Authorization", bearer)
                        .build(),
                )
            }
        }

    return OkHttpProvider
        .base
        .newBuilder()
        .addInterceptor(tempAuthInterceptor)
        .build()
}
