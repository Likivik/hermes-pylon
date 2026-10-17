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
import kotlinx.browser.document
import org.jetbrains.skiko.wasm.onWasmReady
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
    // `onWasmReady` is required: the DOM has to exist before ComposeViewport can
    // append its canvas. In CMP 1.12.2 `viewportContainer` is an `Element`, NOT the
    // container-id String the original API took - passing a String is a compile
    // error ("actual type is 'String', but 'Element' was expected").
    //
    // ComposeViewport sizes the canvas to the container, and Compose injects no CSS
    // of its own, so `html, body` must have an explicit size in index.html or the
    // canvas has zero area and the page renders blank.
    onWasmReady {
        ComposeViewport(viewportContainer = document.body!!) {
            PylonWebEntry()
        }
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
