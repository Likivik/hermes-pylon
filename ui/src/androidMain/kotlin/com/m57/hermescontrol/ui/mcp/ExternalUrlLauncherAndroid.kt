package com.m57.hermescontrol.ui.mcp

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Android opens external URLs in a Chrome Custom Tab, as the MCP screen always has. */
@Composable
actual fun rememberExternalUrlLauncher(): ExternalUrlLauncher {
    val context = LocalContext.current
    return remember(context) {
        object : ExternalUrlLauncher {
            override fun openExternalUrl(url: String): Boolean =
                runCatching {
                    CustomTabsIntent
                        .Builder()
                        .build()
                        .launchUrl(context, Uri.parse(url))
                }.isSuccess
        }
    }
}
