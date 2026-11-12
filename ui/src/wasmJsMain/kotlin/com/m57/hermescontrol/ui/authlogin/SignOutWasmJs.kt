package com.m57.hermescontrol.ui.authlogin

import kotlinx.browser.window

/**
 * Hand the browser over to an HTML page, because the Compose app must never
 * render a sign-in screen on the web: a canvas text field is invisible to every
 * password manager.
 *
 * - Sign out goes through `logout.html`, which destroys the server session and
 *   then redirects. It runs on a page of its own so nothing can cancel it
 *   mid-flight — the failure this replaces.
 * - An expired session goes straight to `login.html`: there is nothing left to
 *   destroy, and asking the server to log out a dead session only adds a round
 *   trip to the path the user is already on.
 *
 * The `pylon.logout` marker is written here and consumed by `logout.html`. That
 * is what makes the page safe to link to: without a marker the page asks instead
 * of acting, so a hostile link cannot force a visitor's session closed.
 */
internal actual fun handleSignOut(reason: SignOutReason): Boolean {
    when (reason) {
        SignOutReason.USER_INITIATED -> {
            runCatching { window.sessionStorage.setItem("pylon.logout", "1") }
            window.location.replace("logout.html")
        }

        SignOutReason.SESSION_EXPIRED -> window.location.replace("login.html")
    }
    // The navigation has started; the caller must not queue another one.
    return true
}
