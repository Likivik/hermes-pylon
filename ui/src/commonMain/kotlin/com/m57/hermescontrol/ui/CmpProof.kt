package com.m57.hermescontrol.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Minimal commonMain composable that proves the Compose Multiplatform plugin
 * and runtime are wired correctly across every target the `:ui` module builds
 * (android + jvm). It carries no behaviour, no theme, no state — the point is
 * only that `@Composable` resolves from commonMain against
 * `org.jetbrains.compose.ui:ui:1.12.2` and `material3:1.12.0-alpha03`.
 *
 * Real UI lives in `:ui/src/androidMain/`. This file is the Phase 2 toolchain
 * proof: when CMP Phase 3 starts moving screens into commonMain, the existing
 * `:ui` build configuration will already support them.
 */
@Composable
fun CmpProof(
    label: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label)
    }
}
