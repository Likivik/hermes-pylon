// Modified from Hy4ri/hermes-mobile for this fork; see NOTICE.

package com.m57.hermescontrol.data.remote

import com.m57.hermescontrol.BuildConfig
import com.m57.hermescontrol.data.local.AuthManager
import io.ktor.client.HttpClient
import okhttp3.Authenticator
import okhttp3.CookieJar
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

/**
 * Provides the Ktor-backed [HermesGatewayApi] for the Android app.
 *
 * Unlike the Retrofit client this replaces, no second HTTP stack is created:
 * the gateway client is built *on top of* the app's [OkHttpProvider] client via
 * `preconfigured`, so OkHttp keeps owning the connection pool, the persistent
 * cookie jar, TLS pinning, [TokenRefreshAuthenticator] and the auth interceptor,
 * and Ktor drives the same objects. That is what lets the REST surface move to
 * the multiplatform floor without changing session behaviour.
 */
object ApiClient {
    @Volatile
    private var client: HttpClient? = null

    @Volatile
    private var gateway: HermesGatewayApi? = null

    /** The current [HermesGatewayApi] instance. Lazily created on first access. */
    val hermesApi: HermesGatewayApi
        get() =
            gateway ?: synchronized(this) {
                gateway ?: buildGateway().also { gateway = it }
            }

    /**
     * Force-rebuild the gateway client (e.g. after settings change).
     *
     * Deliberately does NOT close the outgoing [HttpClient]: it is built on the
     * app's shared [OkHttpProvider] client via `preconfigured`, and Ktor's OkHttp
     * engine `close()` shuts down that client's dispatcher executor and evicts its
     * connection pool. Dropping the reference is enough; the underlying stack is
     * owned by [OkHttpProvider] and is also serving the WebSocket client.
     */
    fun rebuild() {
        synchronized(this) {
            client = null
            gateway = null
        }
    }

    /** Creates a standalone, temporary gateway without modifying the global instance. */
    fun createTempService(
        baseUrl: String,
        token: String,
    ): HermesGatewayApi {
        val endpoint = ServerEndpoint.parseForBuild(baseUrl)
        val tempAuthInterceptor =
            Interceptor { chain ->
                val request =
                    if (token.isNotBlank()) {
                        chain
                            .request()
                            .newBuilder()
                            .addHeader("Authorization", "Bearer $token")
                            .build()
                    } else {
                        chain.request()
                    }
                chain.proceed(request)
            }

        val tempOkHttp =
            OkHttpProvider
                .base
                .newBuilder()
                .addInterceptor(tempAuthInterceptor)
                .build()

        return gatewayOver(endpoint.baseUrl, tempOkHttp)
    }

    /**
     * OkHttp client bound to one endpoint/auth-mode/token snapshot.
     *
     * Media and file-range reads stay on the engine here: they need streaming
     * bodies and `Range` semantics that the JSON floor deliberately does not model.
     */
    internal fun createMediaClient(
        gated: Boolean,
        token: String?,
        cookieHeader: String? = null,
    ): OkHttpClient {
        val auth =
            Interceptor { chain ->
                val request = chain.request()
                if (gated || token.isNullOrBlank()) {
                    chain.proceed(request)
                } else {
                    chain.proceed(request.newBuilder().addHeader("Authorization", "Bearer $token").build())
                }
            }
        val cookie =
            Interceptor { chain ->
                val request = chain.request()
                if (!gated || cookieHeader.isNullOrBlank()) {
                    chain.proceed(request)
                } else {
                    chain.proceed(request.newBuilder().header("Cookie", cookieHeader).build())
                }
            }
        return OkHttpProvider.base.newBuilder()
            // newBuilder retains the base client's live credential behavior;
            // strip it while preserving dispatcher, TLS and pinning state.
            .apply {
                interceptors().clear()
                networkInterceptors().clear()
            }
            .cookieJar(CookieJar.NO_COOKIES)
            .addInterceptor(auth)
            .addInterceptor(cookie)
            .authenticator(Authenticator.NONE)
            .build()
    }

    /** Build a media gateway bound to one endpoint/auth-mode/token snapshot. */
    internal fun createMediaService(
        endpoint: ServerEndpoint,
        gated: Boolean,
        token: String?,
        cookieHeader: String? = null,
    ): HermesGatewayApi = gatewayOver(endpoint.baseUrl, createMediaClient(gated, token, cookieHeader))

    // ── Internal ─────────────────────────────────────────────────────────

    private fun gatewayOver(
        baseUrl: ServerBaseUrl,
        okHttp: OkHttpClient,
        config: HermesHttpConfig = HermesHttpConfig.Default,
    ): HermesGatewayApi {
        val created = createHermesHttpClient(okHttp, config)
        client = created
        return HermesGatewayApi(created, baseUrl)
    }

    private fun buildGateway(): HermesGatewayApi {
        val logging =
            HttpLoggingInterceptor().apply {
                level =
                    if (BuildConfig.DEBUG) {
                        HttpLoggingInterceptor.Level.BASIC
                    } else {
                        HttpLoggingInterceptor.Level.NONE
                    }
            }

        // Loopback mode uses Bearer auth. Gated profiles rely exclusively on
        // the session cookie attached by the shared CookieJar; adding a stale
        // token alongside it causes the dashboard to reject otherwise valid
        // REST requests.
        val authInterceptor =
            Interceptor { chain ->
                val request = chain.request()
                if (AuthManager.isGatedMode()) {
                    return@Interceptor chain.proceed(request)
                }
                val token = AuthManager.getToken()
                if (!token.isNullOrBlank()) {
                    chain.proceed(
                        request
                            .newBuilder()
                            .addHeader("Authorization", "Bearer $token")
                            .build(),
                    )
                } else {
                    chain.proceed(request)
                }
            }

        val okHttp =
            OkHttpProvider
                .base
                .newBuilder()
                .addInterceptor(authInterceptor)
                // A local connection ID is not a server management profile.
                // Preserve explicit request scopes; never infer one here.
                .addInterceptor(logging)
                .authenticator(TokenRefreshAuthenticator)
                .build()

        return gatewayOver(AuthManager.endpointForBuild().baseUrl, okHttp)
    }
}
