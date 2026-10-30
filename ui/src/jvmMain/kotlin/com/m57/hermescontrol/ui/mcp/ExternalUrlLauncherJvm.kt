package com.m57.hermescontrol.ui.mcp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.Desktop
import java.net.URI

/**
 * Desktop opens external URLs in the system browser.
 *
 * [Desktop] is unavailable in headless environments (`java.awt.HeadlessException`)
 * and on platforms without a browser integration, so the call is guarded and
 * degrades to a documented no-op that reports failure.
 */
@Composable
actual fun rememberExternalUrlLauncher(): ExternalUrlLauncher =
    remember {
        object : ExternalUrlLauncher {
            override fun openExternalUrl(url: String): Boolean =
                runCatching {
                    val desktop = Desktop.getDesktop()
                    if (!desktop.isSupported(Desktop.Action.BROWSE)) return@runCatching false
                    desktop.browse(URI(url))
                    true
                }.getOrDefault(false)
        }
    }
