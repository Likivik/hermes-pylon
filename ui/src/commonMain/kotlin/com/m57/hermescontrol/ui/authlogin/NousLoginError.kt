package com.m57.hermescontrol.ui.authlogin

/**
 * A Nous sign-in problem, in the form the login screen can translate.
 *
 * The view model holds no strings and the sign-in engine holds no locale, so the two meet on this
 * type: the engine says *what* went wrong, the screen says it in the user's language.
 */
enum class NousLoginErrorKind {
    /** The encrypted store or the gateway URL is not ready yet. */
    Unavailable,

    /** The platform cannot listen on loopback, so the flow cannot run at all (the web build). */
    Unsupported,

    /** Our own redirect pre-check refused the URL; nothing was opened. */
    RefusedRedirect,

    /** The code was rejected: already used, or the verifier did not match. */
    CodeRejected,

    /** The sign-in expired before it was redeemed. */
    SignInExpired,

    /** The provider redirected with an error instead of a code. */
    ProviderRefused,

    /** The gateway or the provider could not be reached. */
    Network,
}

data class NousLoginError(
    val kind: NousLoginErrorKind,
    /** Untranslated diagnostic. Shown only where a message has a slot for it. */
    val detail: String = "",
) {
    companion object {
        fun of(
            failure: NativeSignInFailure,
            detail: String = "",
        ): NousLoginError =
            NousLoginError(
                when (failure) {
                    NativeSignInFailure.RefusedRedirect -> NousLoginErrorKind.RefusedRedirect
                    NativeSignInFailure.CodeRejected -> NousLoginErrorKind.CodeRejected
                    NativeSignInFailure.SignInExpired -> NousLoginErrorKind.SignInExpired
                    NativeSignInFailure.ProviderRefused -> NousLoginErrorKind.ProviderRefused
                    NativeSignInFailure.Network -> NousLoginErrorKind.Network
                },
                detail,
            )
    }
}
