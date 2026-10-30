// Modified from Hy4ri/hermes-mobile for this fork; see NOTICE.

package com.m57.hermescontrol.data.remote

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
    /** Build a media gateway bound to one endpoint/auth-mode/token snapshot. */
    fun createMediaService(
        endpoint: ServerEndpoint,
        gated: Boolean,
        token: String?,
        cookieHeader: String? = null,
    ): HermesGatewayApi =
        HermesGatewayApi(
            createMediaTransportClient(token = token, cookieHeader = cookieHeader),
            endpoint.baseUrl,
        )
}
