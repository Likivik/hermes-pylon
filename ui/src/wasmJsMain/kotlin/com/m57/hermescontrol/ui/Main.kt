package com.m57.hermescontrol.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeViewport
import com.m57.hermescontrol.generated.resources.Res
import com.m57.hermescontrol.generated.resources.app_name
import com.m57.hermescontrol.generated.resources.landing_title
import org.jetbrains.compose.resources.stringResource

/**
 * Minimal wasmJs/browser entry point that proves the `:ui` web target
 * compiles end to end: the Kotlin/Wasm toolchain, the CMP Compose
 * runtime compiled to wasmJs, and the Compose Resources pipeline.
 *
 * [ComposeViewport] is the current CMP entry API for the browser
 * (replacing the deprecated `CanvasBasedWindow`). It wires the WASM-side
 * `Window`/`Document` body into a `@Composable` lambda so the same
 * commonMain content as [Main] can be rendered. The function lives in
 * `androidx.compose.ui:ui` (the wasmJs klib) and is gated by
 * `ExperimentalComposeUiApi` — JetBrains still considers the entry-point
 * shape experimental, so the `@OptIn` is required.
 *
 * The body renders [CmpProof] plus two `stringResource(Res.string.…)`
 * reads, identical in spirit to the desktop entry: a small screen
 * whose only purpose is to exercise the Compose runtime and the typed
 * `Res` accessor on the wasmJs runtime.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport {
        PylonWebEntry()
    }
}

@Composable
private fun PylonWebEntry() {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
    ) {
        CmpProof(
            label = "Pylon web proof",
            modifier = Modifier.height(64.dp),
        )
        Text(text = "app_name = ${stringResource(Res.string.app_name)}")
        Text(text = "landing_title = ${stringResource(Res.string.landing_title)}")
    }
}
