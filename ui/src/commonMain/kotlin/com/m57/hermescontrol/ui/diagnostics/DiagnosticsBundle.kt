package com.m57.hermescontrol.ui.diagnostics

import com.m57.hermescontrol.util.LogRecord

/**
 * The text a user pastes into a bug report.
 *
 * Plain text on purpose: it survives every clipboard on every target, reads in a
 * chat message, and diffs in a terminal. Everything it contains is already
 * redacted at the sink boundary, so this function must not add fields that
 * bypass that (no headers, no bodies, no tokens).
 */
fun buildDiagnosticsBundle(
    state: DiagnosticsState,
    records: List<LogRecord>,
): String =
    buildString {
        appendLine("=== Pylon diagnostics ===")
        appendLine("target: ${state.environment.target}")
        appendLine("origin: ${state.environment.origin ?: "-"}")
        appendLine("endpoint: ${state.endpoint}")
        appendLine("cleartext: ${state.cleartext}")
        state.securityWarning?.let { appendLine("warning: $it") }
        state.endpointError?.let { appendLine("endpoint_error: $it") }
        appendLine("store_base_url: ${state.storeBaseUrl ?: "-"}")
        appendLine("token_present: ${state.hasToken}")
        appendLine("profile: ${state.selectedProfile ?: "-"}")
        appendLine()
        appendLine("--- checks ---")
        if (state.checks.isEmpty()) {
            appendLine("(none run)")
        } else {
            state.checks.forEach { check ->
                appendLine("${check.name} | ${check.outcome} | ${check.durationMillis} ms | healthy=${check.healthy}")
                appendLine("  ${check.verdict}")
            }
        }
        appendLine()
        appendLine("--- log (${records.size}) ---")
        if (records.isEmpty()) {
            appendLine("(empty)")
        } else {
            records.forEach { appendLine(it.render()) }
        }
    }
