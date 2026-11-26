package com.m57.hermescontrol.ui.authlogin

/**
 * Navigates the current top-level browsing context to [url].
 *
 * The browser sign-in cannot use a new tab: the gateway's cookie flow ends with a redirect back to
 * Pylon, and only the tab that navigated to the gateway comes back. A `window.open` tab would leave
 * the original tab on the login screen — signed in in one tab, still logged out in the other — and
 * popup blockers can refuse it outright.
 *
 * Only the browser build has a top-level context to navigate. The other targets never reach this:
 * they run the native loopback flow instead.
 */
expect fun navigateTopLevel(url: String)
