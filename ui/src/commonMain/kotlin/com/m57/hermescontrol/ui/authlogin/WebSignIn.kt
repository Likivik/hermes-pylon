package com.m57.hermescontrol.ui.authlogin

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The browser half of Nous sign-in.
 *
 * A browser cannot run the native flow: `/auth/native/authorize` delivers the code to a loopback
 * listener on the device, and a page has no such listener. So the web build uses the gateway's own
 * browser flow instead — navigate to `/auth/login`, the gateway sets its session cookie on its own
 * origin, and the redirect brings the user back to Pylon already authenticated. No token ever
 * reaches browser storage.
 *
 * [next] is constrained here rather than left to the gateway, for the same reason the native
 * redirect is checked locally: the gateway validates it only after the user has finished at the
 * provider. A bad `next` would cost a completed sign-in.
 */
object WebSignIn {
    const val DEFAULT_PROVIDER = "nous"
    const val DEFAULT_NEXT = "/"

    /**
     * Whether to offer the browser button at all.
     *
     * The gateway is the only thing that knows if it has an interactive provider, so this asks for
     * the provider by name. Anything else — an empty list, a deployment whose providers are
     * password-only — means no button, because the alternative is a provider round trip that ends
     * on a 404.
     */
    fun offersBrowserSignIn(providers: List<String>): Boolean = DEFAULT_PROVIDER in providers

    /** A path on the gateway's own origin. Absolute URLs, `//host` and control characters are out. */
    fun nextRejection(next: String): String? =
        when {
            next.isEmpty() -> "the post-login path is empty"
            !next.startsWith("/") -> "the post-login path must start with a slash"
            next.startsWith("//") -> "the post-login path must not name a host"
            next.any { it.isWhitespace() || it.isISOControl() } -> "the post-login path must not contain whitespace"
            else -> null
        }

    /**
     * The URL to hand the browser.
     *
     * The cookie flow needs the page the user returns to on the gateway's origin. When Pylon is
     * served from somewhere else the cookie is third-party and the browser will refuse it — that is
     * a deployment question, not something this builder can detect.
     */
    fun authorizeUrl(
        gatewayBaseUrl: String,
        provider: String = DEFAULT_PROVIDER,
        next: String = DEFAULT_NEXT,
    ): String {
        require(provider.isNotBlank()) { "provider must not be blank" }
        nextRejection(next)?.let { throw IllegalArgumentException(it) }

        val base = gatewayBaseUrl.trimEnd('/')
        return "$base/auth/login?provider=${encodeQuery(provider)}&next=${encodeQuery(next)}"
    }

    /**
     * The provider names in a `/api/auth/providers` body.
     *
     * Anything unreadable — a `503` body, an error page, a shape change — yields an empty list.
     * The caller reads empty as "offer no browser sign-in", which is the safe direction: a missing
     * button costs nothing, a wrong button costs a provider round trip that ends on a 404.
     */
    fun providerNames(body: String): List<String> =
        runCatching {
            Json
                .parseToJsonElement(body)
                .jsonObject["providers"]
                ?.jsonArray
                ?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
                .orEmpty()
        }.getOrDefault(emptyList())

    /** Percent-encode everything that is not unreserved, which is what the gateway expects. */
    private fun encodeQuery(value: String): String =
        buildString {
            for (byte in value.encodeToByteArray()) {
                val c = byte.toInt() and 0xFF
                val ch = c.toChar()
                if (ch.isAsciiLetterOrDigit() || ch in "-._~") {
                    append(ch)
                } else {
                    append('%')
                    append(HEX[c shr 4])
                    append(HEX[c and 0x0F])
                }
            }
        }

    private fun Char.isAsciiLetterOrDigit(): Boolean = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'

    private const val HEX = "0123456789ABCDEF"
}
