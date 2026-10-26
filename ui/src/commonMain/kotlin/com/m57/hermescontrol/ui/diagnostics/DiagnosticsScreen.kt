package com.m57.hermescontrol.ui.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.m57.hermescontrol.ui.keys.rememberSensitiveCopy
import com.m57.hermescontrol.util.LogSeverity

/**
 * The permanent answer to "why is the gateway unreachable?".
 *
 * Three blocks, in the order a support conversation needs them: what this build
 * is and where it points, what the probes concluded, and every request the app
 * actually made with its timing.
 */
@Composable
fun DiagnosticsScreen(modifier: Modifier = Modifier) {
    val viewModel = remember { DiagnosticsViewModel() }
    val state by viewModel.state.collectAsState()
    val events by viewModel.events.collectAsState()
    val copy = rememberSensitiveCopy()

    LaunchedEffect(Unit) { viewModel.runChecks() }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Diagnostics", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.runChecks() }, enabled = !state.running) {
                    Text(if (state.running) "Checking…" else "Run checks")
                }
                OutlinedButton(onClick = { viewModel.refreshFacts() }) { Text("Refresh") }
                OutlinedButton(onClick = { copy("Pylon diagnostics", buildDiagnosticsBundle(state, events)) }) {
                    Text("Copy")
                }
            }
        }

        if (state.running) {
            CircularProgressIndicator(modifier = Modifier.padding(vertical = 8.dp))
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item { SectionHeader("Build") }
            item { Fact("Target", state.environment.target) }
            item { Fact("Page origin", state.environment.origin ?: "—") }
            item { Fact("Endpoint", state.endpoint) }
            item { Fact("Cleartext", if (state.cleartext) "yes (insecure)" else "no") }
            state.securityWarning?.let { warning -> item { Fact("Warning", warning) } }
            state.endpointError?.let { error -> item { Fact("Endpoint error", error) } }
            item { Fact("Store base URL", state.storeBaseUrl ?: "—") }
            item { Fact("Token present", if (state.hasToken) "yes" else "no") }
            item { Fact("Selected profile", state.selectedProfile ?: "—") }

            item { SectionHeader("Checks") }
            if (state.checks.isEmpty()) {
                item { Text("No checks run yet.", style = MaterialTheme.typography.bodySmall) }
            }
            items(state.checks) { check ->
                CheckRow(check)
            }

            item { SectionHeader("Recent log (${events.size})") }
            if (events.isEmpty()) {
                item {
                    Text(
                        "Nothing recorded yet. Every request appears here as the app makes it — this is the " +
                            "evidence that separates a wrong URL from a proxy that never answers.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            items(events) { record ->
                Text(
                    text = record.render(),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color =
                        when (record.severity) {
                            LogSeverity.ERROR -> MaterialTheme.colorScheme.error
                            LogSeverity.WARN -> MaterialTheme.colorScheme.onSurface
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Column {
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun Fact(
    label: String,
    value: String,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun CheckRow(check: DiagnosticCheck) {
    Column {
        Text(
            text = "${check.name} — ${check.outcome} (${check.durationMillis} ms)",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color =
                if (check.healthy) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.error
                },
        )
        Text(check.verdict, style = MaterialTheme.typography.bodySmall)
    }
}
