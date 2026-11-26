package com.m57.hermescontrol.ui.authlogin

import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.appendPathSegments

/**
 * Builds and validates the gateway's native sign-in URLs.
 *
 * The gateway accepts exactly one redirect shape: a plain `http://` URL on the loopback
 * interface, spelled with an IP literal. It rejects `localhost` outright (RFC 8252 §8.3),
 * rejects `https`, and rejects any custom scheme. Because that rejection happens *after* the
 * provider round trip, a wrong shape costs the user a full login; [redirectRejection] is
 * therefore checked locally before the browser is opened.
 */
internal object NativeAuthorize {
    private val ACCEPTED_HOSTS = setOf("127.0.0.1", "::1")

    /** The redirect the app's loopback listener will be waiting on. */
    fun loopbackRedirect(port: Int): String = "http://127.0.0.1:$port/callback"

    /**
     * The URL that starts the flow, opened in a Custom Tab.
     *
     * The path is appended to the gateway's base URL, so a gateway mounted under a path prefix
     * keeps it. Parameters are encoded by the builder rather than concatenated: `redirect_uri`
     * contains `://`, and a raw colon in a query value is not a valid URL.
     */
    fun url(
        gatewayBaseUrl: String,
        provider: String,
        redirectUri: String,
        codeChallenge: String,
        state: String,
    ): String =
        URLBuilder(Url(gatewayBaseUrl)).apply {
            appendPathSegments("auth", "native", "authorize")
            parameters.append("provider", provider)
            parameters.append("code_challenge", codeChallenge)
            parameters.append("code_challenge_method", "S256")
            parameters.append("redirect_uri", redirectUri)
            parameters.append("state", state)
        }.buildString()

    /**
     * Null when the gateway will accept [redirectUri]; otherwise why it will not.
     *
     * Mirrors `_validate_loopback_redirect_uri` in `hermes_cli/dashboard_auth/routes.py`. It is
     * deliberately a *local* copy: the gateway's message arrives after the user has already
     * authenticated, which is the worst possible moment to learn the URL was malformed.
     */
    fun redirectRejection(redirectUri: String): String? {
        if (redirectUri.isBlank()) return "redirect_uri required"
        if (redirectUri.any { it.code <= 0x20 || it.code == 0x7F }) return "must not contain controls"
        if (redirectUri.contains('\\')) return "must not contain a backslash"
        if (!redirectUri.all { it.code < 0x80 }) return "must be ASCII"
        if (redirectUri.contains('#')) return "must not contain a fragment"

        val parsed =
            runCatching { Url(redirectUri) }.getOrNull()
                ?: return "must be a valid URL"
        if (parsed.protocol.name != "http") return "must be http:// on the loopback interface"
        if (!parsed.user.isNullOrEmpty() || !parsed.password.isNullOrEmpty()) return "must not contain userinfo"
        val host = parsed.host.trim('[', ']')
        if (host !in ACCEPTED_HOSTS) return "host must be a loopback IP literal (127.0.0.1 / ::1)"
        return null
    }
}
