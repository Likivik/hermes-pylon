package com.m57.hermescontrol.ui.authlogin

import com.m57.hermescontrol.data.local.NativeTokenStore

sealed interface NativeSignInResult {
    data class Success(
        val token: NativeToken,
    ) : NativeSignInResult

    /** The browser never called back: the tab was closed, or the provider was abandoned. */
    data object Cancelled : NativeSignInResult

    data class Failed(
        val reason: String,
    ) : NativeSignInResult

    /** This platform cannot run the flow at all. The web build is the expected caller. */
    data object Unsupported : NativeSignInResult
}

/**
 * The whole native sign-in, in the order the gateway requires it.
 *
 * Listener first, then URL: the redirect has to name the port the listener is already bound to,
 * and the listener must be waiting before the browser can arrive. Doing it the other way round
 * loses the first callback.
 *
 * [openBrowser] is a parameter rather than a call to an intent: the caller owns how a URL is
 * opened (Custom Tab on Android), which keeps this class free of platform types and lets the test
 * act as the browser.
 */
class NativeSignIn(
    private val exchange: NativeTokenExchange,
    private val store: NativeTokenStore,
    private val timeoutMillis: Long = DEFAULT_LOOPBACK_TIMEOUT_MILLIS,
    private val openListener: (Long) -> LoopbackListener = ::openLoopbackListener,
) {
    suspend fun signIn(
        gatewayBaseUrl: String,
        provider: String = DEFAULT_PROVIDER,
        openBrowser: (String) -> Unit,
    ): NativeSignInResult {
        val listener =
            try {
                openListener(timeoutMillis)
            } catch (_: UnsupportedOperationException) {
                return NativeSignInResult.Unsupported
            }

        try {
            val verifier = Pkce.newVerifier()
            val state = Pkce.newState()
            val redirect = NativeAuthorize.loopbackRedirect(listener.port)

            NativeAuthorize.redirectRejection(redirect)?.let {
                return NativeSignInResult.Failed("refusing to start: $it")
            }

            openBrowser(
                NativeAuthorize.url(
                    gatewayBaseUrl = gatewayBaseUrl,
                    provider = provider,
                    redirectUri = redirect,
                    codeChallenge = Pkce.challengeFor(verifier),
                    state = state,
                ),
            )

            return when (val outcome = listener.awaitCallback(state)) {
                is LoopbackOutcome.TimedOut -> NativeSignInResult.Cancelled
                is LoopbackOutcome.Rejected -> NativeSignInResult.Failed(outcome.reason)
                is LoopbackOutcome.Received -> redeem(gatewayBaseUrl, outcome.callback.code, verifier)
            }
        } finally {
            listener.close()
        }
    }

    private suspend fun redeem(
        gatewayBaseUrl: String,
        code: String,
        verifier: String,
    ): NativeSignInResult =
        when (val result = exchange.redeem(gatewayBaseUrl, code, verifier)) {
            is TokenResult.Success -> {
                store.save(result.token)
                NativeSignInResult.Success(result.token)
            }
            is TokenResult.InvalidCode -> NativeSignInResult.Failed("the sign-in code was rejected; try again")
            is TokenResult.SessionExpired -> NativeSignInResult.Failed("the sign-in expired; try again")
            is TokenResult.Retryable -> NativeSignInResult.Failed(result.reason)
        }

    companion object {
        const val DEFAULT_PROVIDER = "nous"
    }
}
