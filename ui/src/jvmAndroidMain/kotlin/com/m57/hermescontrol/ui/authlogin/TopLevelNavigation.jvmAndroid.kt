package com.m57.hermescontrol.ui.authlogin

/**
 * There is no top-level browsing context on a device, and none is needed: Android and the JVM run
 * the native loopback flow, where the browser is a separate application and the redirect comes
 * back on a socket instead of the page.
 *
 * Reaching this is a routing mistake, not a runtime condition, so it fails loudly rather than
 * pretending to navigate.
 */
actual fun navigateTopLevel(url: String): Unit =
    throw UnsupportedOperationException("the browser sign-in flow is web-only")
