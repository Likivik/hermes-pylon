package com.m57.hermescontrol.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.m57.hermescontrol.generated.resources.Res
import com.m57.hermescontrol.generated.resources.app_name
import com.m57.hermescontrol.generated.resources.landing_title
import org.jetbrains.compose.resources.stringResource

/**
 * Minimal desktop entry point that proves the `:ui` JVM target compiles
 * end to end: the Compose desktop `application { Window { ... } }` host,
 * the CMP Compose runtime, and the Compose Resources pipeline.
 *
 * The window renders [CmpProof] plus two `stringResource(Res.string.…)`
 * reads. The proof is deliberately tiny — the real Android UI lives in
 * `:ui/src/androidMain/` and is unreachable from `jvmMain`. Adding a
 * desktop entry point that links against commonMain is enough to prove
 * the JVM toolchain can resolve CMP's Compose runtime, the typed
 * `Res` accessor generated from `ui/src/commonMain/composeResources/`,
 * and the generated `stringResource` overloads.
 *
 * Title is hard-coded to "Pylon"; `Res.string.app_name` is rendered in
 * the body so the JVM toolchain actually exercises the resources lookup
 * (a value of `""` would still satisfy the type check).
 */
fun main() {
    // Desktop console sink; the ring is always on inside HermesLog.
    com.m57.hermescontrol.util.HermesLog.install(com.m57.hermescontrol.util.ConsoleLogSink())
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Pylon",
        ) {
            PylonDesktopEntry()
        }
    }
}

@Composable
private fun PylonDesktopEntry() {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
    ) {
        CmpProof(
            label = "Pylon desktop proof",
            modifier = Modifier.height(64.dp),
        )
        Text(text = "app_name = ${stringResource(Res.string.app_name)}")
        Text(text = "landing_title = ${stringResource(Res.string.landing_title)}")
    }
}
