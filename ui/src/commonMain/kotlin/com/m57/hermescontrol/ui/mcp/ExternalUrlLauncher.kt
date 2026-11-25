package com.m57.hermescontrol.ui.mcp

import androidx.compose.runtime.Composable

/**
 * Opens an external URL — an MCP OAuth authorization page — outside the app.
 *
 * The dashboard hands the app an HTTPS authorization URL that the user has to
 * complete in a real browser. Which browser, and how it is launched, is a
 * platform concern: Android opens a Chrome Custom Tab, the desktop opens the
 * system browser, the web opens a new tab.
 *
 * The launcher is scoped to a composition because Android's implementation
 * needs the Activity context; there is no global context to borrow.
 */
interface ExternalUrlLauncher {
    /**
     * Opens [url] in the platform browser.
     *
     * @return true when the platform accepted the request.
     */
    fun openExternalUrl(url: String): Boolean
}

/** The external-URL launcher for the current composition. */
@Composable
expect fun rememberExternalUrlLauncher(): ExternalUrlLauncher
