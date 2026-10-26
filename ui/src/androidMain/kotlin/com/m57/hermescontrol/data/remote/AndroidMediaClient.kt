// Modified from Hy4ri/hermes-mobile for this fork; see NOTICE.

package com.m57.hermescontrol.data.remote

import okhttp3.Authenticator
import okhttp3.CookieJar
import okhttp3.Interceptor
import okhttp3.OkHttpClient

/**
 * Android-only media/file plumbing.
 *
 * These do not belong on the shared JSON floor: managed-file reads need
 * streaming `Range` bodies that the JSON surface deliberately does not model, and
 * they bind to one endpoint/auth-mode/token snapshot so a mid-download token
 * refresh cannot change what the request is authenticated with.
 *
 * Everything REST-shaped lives in the shared [ApiClient]; this file only
 * constructs OkHttp clients, so it is intentionally not part of the seam.
 */
object AndroidMediaClient {
    /**
     * OkHttp client bound to one endpoint/auth-mode/token snapshot.
     *
     * Media and file-range reads stay on the engine here: they need streaming
     * bodies and `Range` semantics that the JSON floor does not model.
     */
    fun createMediaClient(
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
    fun createMediaService(
        endpoint: ServerEndpoint,
        gated: Boolean,
        token: String?,
        cookieHeader: String? = null,
    ): HermesGatewayApi =
        HermesGatewayApi(
            createHermesHttpClient(createMediaClient(gated, token, cookieHeader)),
            endpoint.baseUrl,
        )
}
