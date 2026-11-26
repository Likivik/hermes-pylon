package com.m57.hermescontrol.ui.authlogin

import com.m57.hermescontrol.data.security.secureRandomBytes
import com.m57.hermescontrol.util.sha256Bytes
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * PKCE (RFC 7636) and anti-CSRF state for the gateway's native sign-in.
 *
 * The gateway requires `code_challenge_method=S256` and rejects anything else, so there is
 * no "plain" option to support. Both values are base64url **without** padding: the gateway
 * compares the challenge as an opaque string, and a `=` suffix would simply not match.
 */
internal object Pkce {
    /** 32 random bytes -> 43 base64url characters, the RFC 7636 minimum length. */
    const val VERIFIER_BYTES = 32

    /** 16 random bytes -> 22 base64url characters. Long enough to be unguessable in a state check. */
    const val STATE_BYTES = 16

    @OptIn(ExperimentalEncodingApi::class)
    private val URL_SAFE_NO_PADDING: Base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

    /** A fresh verifier. Kept secret: it is sent only in the token exchange. */
    fun newVerifier(): String = base64Url(secureRandomBytes(VERIFIER_BYTES))

    /** A fresh `state`. Sent in the authorize URL and checked again on the loopback callback. */
    fun newState(): String = base64Url(secureRandomBytes(STATE_BYTES))

    /** `BASE64URL(SHA256(ASCII(verifier)))`, the S256 challenge. */
    fun challengeFor(verifier: String): String = base64Url(sha256Bytes(verifier.encodeToByteArray()))

    private fun base64Url(bytes: ByteArray): String = URL_SAFE_NO_PADDING.encode(bytes)
}
