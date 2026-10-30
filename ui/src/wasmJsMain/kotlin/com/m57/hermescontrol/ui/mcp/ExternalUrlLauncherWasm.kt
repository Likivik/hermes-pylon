package com.m57.hermescontrol.ui.mcp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlin.js.ExperimentalWasmJsInterop

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """(raw) => {
        try {
            window.open(raw, '_blank');
            return true;
        } catch (e) {
            return false;
        }
    }""",
)
private external fun jsOpenExternalUrl(raw: String): Boolean

/** The web opens external URLs in a new browser tab. */
@Composable
actual fun rememberExternalUrlLauncher(): ExternalUrlLauncher =
    remember {
        object : ExternalUrlLauncher {
            override fun openExternalUrl(url: String): Boolean = jsOpenExternalUrl(url)
        }
    }
