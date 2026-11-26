package com.m57.hermescontrol.ui.authlogin

/**
 * The bearer pair the gateway returns from `/auth/native/token` and `/auth/native/refresh`.
 *
 * [expiresAtEpochSeconds] is nullable on purpose. The field arrives as `expires_at` from the
 * gateway's session object, and its unit is not documented; rather than guess and either
 * refresh on every call (milliseconds read as seconds) or never refresh (the reverse), an
 * unreadable value is treated as "unknown" and expiry is discovered from a 401 instead.
 */
data class NativeToken(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSeconds: Long?,
    val provider: String,
) {
    /** True when [nowEpochSeconds] is within [skewSeconds] of the known expiry. */
    fun needsRefresh(
        nowEpochSeconds: Long,
        skewSeconds: Long = DEFAULT_SKEW_SECONDS,
    ): Boolean {
        val expiry = expiresAtEpochSeconds ?: return false
        return expiry - nowEpochSeconds <= skewSeconds
    }

    /** Never let a token reach a log line or a crash report. */
    override fun toString(): String = "NativeToken(provider=$provider, expiresAt=$expiresAtEpochSeconds)"

    companion object {
        /** Refresh slightly early: a token that expires in flight fails the request it was for. */
        const val DEFAULT_SKEW_SECONDS = 120L
    }
}

/**
 * Outcome of a token call, as the UI needs it.
 *
 * Three states, not one exception type: `InvalidCode` and `SessionExpired` both mean "start a new
 * sign-in", while `Retryable` means "keep what you have and try again". Collapsing them is how a
 * transient 503 turns into a forced logout.
 */
sealed interface TokenResult {
    data class Success(
        val token: NativeToken,
    ) : TokenResult

    /** `400` from the token endpoint: the code was already redeemed, or the verifier did not match. */
    data object InvalidCode : TokenResult

    /** `401 session_expired`: the provider rejected the refresh token. The user must sign in again. */
    data object SessionExpired : TokenResult

    /** `503`, a transport failure, or an unreadable body. The held token is still worth keeping. */
    data class Retryable(
        val reason: String,
    ) : TokenResult
}
