package com.m57.hermescontrol.ui.diagnostics

import kotlinx.browser.window

actual fun diagnosticsEnvironment(): DiagnosticsEnvironment =
    DiagnosticsEnvironment(
        target = "Web (${window.navigator.userAgent.substringBefore(')')})",
        origin = window.location.origin,
    )
