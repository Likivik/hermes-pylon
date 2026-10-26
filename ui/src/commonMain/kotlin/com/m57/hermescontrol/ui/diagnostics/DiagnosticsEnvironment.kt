package com.m57.hermescontrol.ui.diagnostics

/**
 * Facts only the platform can answer, shown at the top of the diagnostics
 * screen so a bug report carries them.
 *
 * @param target human-readable platform/version, e.g. `Web`, `Android 34`.
 * @param origin what the app believes it is talking to *by definition*: the
 *   page origin on wasm, the `hermes.gatewayUrl` property on desktop, nothing on
 *   Android (there the profile in the store is authoritative).
 */
data class DiagnosticsEnvironment(
    val target: String,
    val origin: String?,
)

expect fun diagnosticsEnvironment(): DiagnosticsEnvironment
