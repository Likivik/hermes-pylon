package com.m57.hermescontrol.ui.authlogin

import kotlin.js.ExperimentalWasmJsInterop

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """(raw) => {
        window.location.href = raw;
    }""",
)
private external fun jsNavigateTopLevel(raw: String)

/**
 * Same-tab navigation to the gateway's authorize URL.
 *
 * `location.href` rather than `window.open`: the gateway sets its cookie and redirects back, and
 * only this tab receives that redirect.
 */
actual fun navigateTopLevel(url: String) {
    jsNavigateTopLevel(url)
}
