package com.m57.hermescontrol.ui.authlogin

import com.m57.hermescontrol.data.local.NativeTokenStore

/**
 * Why a sign-in did not complete.
 *
 * Typed rather than a message: this layer has no string resources and no locale, so any text it
 * produced would be untranslatable and would bypass the resolver every other message on the login
 * screen goes through. [NativeSignInResult.Failed.detail] carries the untranslated diagnostic.
 */
enum class NativeSignInFailure {
    /** Our own pre-check rejected the redirect URL; the flow never started. */
    RefusedRedirect,

    /** The token endpoint returned `400`: the code was already used, or the verifier did not match. */
    CodeRejected,

    /** `401 session_expired` from the token endpoint. */
    SignInExpired,

    /** The provider redirected with `error=` instead of a code. */
    ProviderRefused,

    /** `503` or a transport failure. Worth retrying. */
    Network,
}

sealed interface NativeSignInResult {
    data class Success(
        val token: NativeToken,
    ) : NativeSignInResult

    /** The browser never called back: the tab was closed, or the provider was abandoned. */
    data object Cancelled : NativeSignInResult

    data class Failed(
        val failure: NativeSignInFailure,
        val detail: String = "",
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
                return NativeSignInResult.Failed(NativeSignInFailure.RefusedRedirect, it)
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
                is LoopbackOutcome.Rejected ->
                    NativeSignInResult.Failed(
                        NativeSignInFailure.ProviderRefused,
                        outcome.reason,
                    )
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
            is TokenResult.InvalidCode -> NativeSignInResult.Failed(NativeSignInFailure.CodeRejected)
            is TokenResult.SessionExpired -> NativeSignInResult.Failed(NativeSignInFailure.SignInExpired)
            is TokenResult.Retryable -> NativeSignInResult.Failed(NativeSignInFailure.Network, result.reason)
        }

    companion object {
        const val DEFAULT_PROVIDER = "nous"
    }
}
