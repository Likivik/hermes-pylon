package com.m57.hermescontrol.ui.diagnostics

actual fun diagnosticsEnvironment(): DiagnosticsEnvironment =
    DiagnosticsEnvironment(
        target = "Desktop (${System.getProperty("os.name") ?: "unknown"})",
        origin = System.getProperty("hermes.gatewayUrl"),
    )
