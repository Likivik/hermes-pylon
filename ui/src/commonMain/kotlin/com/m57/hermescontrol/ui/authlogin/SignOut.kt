package com.m57.hermescontrol.ui.authlogin

/**
 * What the app should do when the session ends, on the platform that owns it.
 *
 * On Android and desktop the session lives in the app's own store, so clearing
 * it and letting navigation react is the whole story. On the web the session is
 * an HttpOnly cookie the app cannot read or delete: the browser owns it, and the
 * only way to end it is to ask the server and then hand the browser to an HTML
 * page. Doing that from a screen's ViewModel is what broke here before — clearing
 * the store recomposes the screen away, the ViewModel is cleared, and the
 * coroutine that was supposed to log out is cancelled with it. So the navigation
 * is a synchronous platform call, not a coroutine.
 */
internal enum class SignOutReason {
    /** The user pressed sign out: the server session should be destroyed. */
    USER_INITIATED,

    /** A request came back 401: the session is already gone, just leave. */
    SESSION_EXPIRED,
}

/**
 * @return true when the platform moved the browser itself, so the caller must not
 *   also raise the in-app latch — on the web that latch would navigate to
 *   `login.html` and win the race against `logout.html`, leaving the server
 *   session alive. false means "the UI handles it", which is what native does.
 */
internal expect fun handleSignOut(reason: SignOutReason): Boolean
