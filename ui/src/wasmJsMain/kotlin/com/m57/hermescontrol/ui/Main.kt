package com.m57.hermescontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeViewport
import com.m57.hermescontrol.data.model.StatusResponse
import com.m57.hermescontrol.data.remote.CleartextPolicy
import com.m57.hermescontrol.data.remote.HermesGatewayApi
import com.m57.hermescontrol.data.remote.ServerEndpoint
import com.m57.hermescontrol.data.remote.createHermesHttpClient
import com.m57.hermescontrol.data.theme.ThemePreference
import com.m57.hermescontrol.generated.resources.Res
import com.m57.hermescontrol.generated.resources.app_name
import com.m57.hermescontrol.generated.resources.landing_title
import com.m57.hermescontrol.theme.HermesControlTheme
import com.m57.hermescontrol.ui.icons.MaterialSymbol
import com.m57.hermescontrol.ui.icons.MaterialSymbolPickerSection
import com.m57.hermescontrol.ui.landing.LandingScreen
import kotlinx.browser.document
import kotlinx.browser.window
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.skiko.wasm.onWasmReady

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
 * [PylonWebEntry] now does more than prove "did the runtime load". It
 *   - mounts the real Pylon theme (dark by default; the dynamic-colour
 *     seam returns null on wasmJs, so the preset's resolved scheme is
 *     used);
 *   - renders [LandingScreen] with the real theme so the title,
 *     subtitle, and login button render with the brand palette and
 *     typography;
 *   - still fires the same Ktor `GET /api/status` against the Hermes
 *     gateway and renders the gateway status lines (version / running /
 *     auth required / active sessions) ABOVE the landing content so
 *     the operational telemetry stays visible alongside the themed
 *     screen (the landing content fills the remaining viewport height,
 *     the status block sits compactly at the top).
 *
 * Why the base URL is the dev server's origin, not the gateway's:
 *
 *   The page is served from `http://127.0.0.1:8080` locally and from
 *   `https://serenity.oryx-galaxy.ts.net:8445` over the tailnet (which
 *   tailscale-serve → 127.0.0.1:8080). The browser cannot reach the
 *   gateway's loopback (127.0.0.1:9119) from the tailnet origin, and
 *   cross-origin requests to 127.0.0.1 from the tailnet origin would be
 *   CORS-blocked anyway. The webpack config (see
 *   `ui/webpack.config.d/gatewayProxy.js`) adds a `proxy: [/api → http://127.0.0.1:9119]`
 *   rule so same-origin `/api/status` requests land at the gateway.
 *   Pointing the Ktor client at `http://127.0.0.1:8080/` therefore
 *   reaches the gateway transparently in both environments.
 *
 *   Cleartext is allowed explicitly because the dev server is plain
 *   HTTP. [ServerEndpoint.parse] would otherwise throw with
 *   "Cleartext HTTP is disabled in this build".
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
            HermesControlTheme(themePreference = ThemePreference.DARK) {
                PylonWebEntry()
            }
        }
    }
}

private sealed interface GatewayStatusUi {
    data object Loading : GatewayStatusUi

    data class Loaded(
        val version: String?,
        val gatewayRunning: Boolean?,
        val authRequired: Boolean?,
        val activeSessions: Int?,
        val errorBody: String? = null,
    ) : GatewayStatusUi

    data class Failed(val message: String) : GatewayStatusUi
}

@Composable
private fun PylonWebEntry() {
    // Icon-picker proof surface: `?icons` renders the Material Symbols picker
    // instead of the landing page. Same component the rail would host, so the
    // screenshot is of production code rather than a mock.
    if (window.location.search.contains("icons")) {
        var picked by remember { mutableStateOf("bolt") }
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Material Symbols picker",
                style = MaterialTheme.typography.titleMedium,
            )
            MaterialSymbolPickerSection(
                selected = picked,
                onSelect = { picked = it },
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "selected = $picked",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                MaterialSymbol(
                    name = picked,
                    size = 40.dp,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        return
    }

    var status by remember { mutableStateOf<GatewayStatusUi>(GatewayStatusUi.Loading) }

    LaunchedEffect(Unit) {
        // The Ktor `Js` engine's `fetch()` is non-blocking — it returns a
        // Promise that resolves on the browser microtask queue, so the
        // coroutine suspends without occupying a worker thread. We do NOT
        // switch to `Dispatchers.Default`: the wasmJs build's "Default"
        // dispatcher is a stub (browsers are single-threaded), and routing
        // the result back through `withContext(Default)` can strand the
        // resumed continuation outside the Compose recomposition cycle.
        status = fetchGatewayStatus()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Landing surface takes the full viewport — the actual screen a
        // visitor sees.
        LandingScreen(
            onAuthLogin = {},
            modifier = Modifier.fillMaxSize(),
        )

        // Gateway status overlay pinned to the bottom of the viewport:
        // small body typography on a translucent surface so both the
        // themed landing content AND the operational telemetry remain
        // visible at the same time. This is the dimension that proves
        // the wasmJs target renders the real theme (status text inherits
        // the dark scheme's onBackground colour) without sacrificing the
        // existing gateway-status telemetry.
        GatewayStatusOverlay(
            status = status,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
        )
    }
}

@Composable
private fun GatewayStatusOverlay(
    status: GatewayStatusUi,
    modifier: Modifier = Modifier,
) {
    val smallStyle =
        MaterialTheme.typography.bodySmall.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = "Pylon web proof",
            style =
                MaterialTheme.typography.labelMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
        )
        Text(
            text = "app_name = ${stringResource(Res.string.app_name)}",
            style = smallStyle,
        )
        Text(
            text = "landing_title = ${stringResource(Res.string.landing_title)}",
            style = smallStyle,
        )
        when (val s = status) {
            GatewayStatusUi.Loading -> Text(text = "gateway status: loading…", style = smallStyle)
            is GatewayStatusUi.Loaded -> {
                Text(text = "gateway version: ${s.version ?: "<unknown>"}", style = smallStyle)
                Text(text = "gateway running: ${s.gatewayRunning ?: "<unknown>"}", style = smallStyle)
                Text(text = "auth required: ${s.authRequired ?: "<unknown>"}", style = smallStyle)
                if (s.activeSessions != null) {
                    Text(text = "active sessions: ${s.activeSessions}", style = smallStyle)
                }
                if (s.errorBody != null) {
                    Text(text = "note: ${s.errorBody}", style = smallStyle)
                }
            }
            is GatewayStatusUi.Failed ->
                Text(
                    text = "gateway status: FAILED — ${s.message}",
                    style = smallStyle,
                )
        }
    }
}

/**
 * One-shot call to GET /api/status via the shared Ktor client.
 *
 * The HTTP client is built via [createHermesHttpClient] so this code path
 * is byte-identical to the Android and JVM call sites; only the engine
 * underneath differs by target. The wasmJs target relies on the
 * browser's `fetch` via the Ktor `Js` engine selected by
 * `ktor-client-engine-defaults`.
 *
 * Returns a [GatewayStatusUi.Loaded] even for HTTP error codes, because
 * the gateway's response shape is itself diagnostic — a "loaded" with a
 * 503 body tells us the proxy and serialization both reached the server.
 *
 * Suspending; call from a coroutine scope. On wasmJs the Ktor `Js`
 * engine wraps the browser's `fetch`, so this does NOT block a worker
 * thread — the suspension point is the fetch Promise's microtask.
 */
private suspend fun fetchGatewayStatus(): GatewayStatusUi {
    // Same-origin base URL: the webpack dev-server's proxy (/api →
    // 127.0.0.1:9119) terminates the request at the gateway. We must use
    // the page's own origin so the request stays same-origin — pointing
    // the Ktor client at the loopback dev server over the tailnet would
    // be a cross-origin HTTPS→HTTP fetch and the browser blocks it
    // without CORS headers (the proxy does not add any).
    //
    // `window.location.origin` is the scheme+host+port the page was
    // loaded from: `http://127.0.0.1:8080` locally,
    // `https://serenity.oryx-galaxy.ts.net:8445` over the tailnet. The
    // dev server is plain HTTP on loopback but the tailnet origin is
    // HTTPS, so we have to pick the cleartext policy based on the scheme.
    val origin = window.location.origin
    val isCleartext = origin.startsWith("http://")
    val policy = if (isCleartext) CleartextPolicy.ALLOW_WITH_WARNING else CleartextPolicy.DENY
    val endpoint =
        try {
            ServerEndpoint.parse(
                rawValue = "$origin/",
                cleartextPolicy = policy,
            )
        } catch (e: IllegalArgumentException) {
            return GatewayStatusUi.Failed("bad endpoint: ${e.message}")
        }

    val api = HermesGatewayApi(createHermesHttpClient(), endpoint.baseUrl)

    return try {
        val response = api.getStatus()
        if (!response.isSuccessful) {
            return GatewayStatusUi.Loaded(
                version = null,
                gatewayRunning = null,
                authRequired = null,
                activeSessions = null,
                errorBody = "HTTP ${response.code}: ${response.errorBody()?.take(200)}",
            )
        }
        val body: StatusResponse? = response.body()
        GatewayStatusUi.Loaded(
            version = body?.version,
            gatewayRunning = body?.gateway_running,
            authRequired = body?.auth_required,
            activeSessions = body?.active_sessions,
        )
    } catch (e: Throwable) {
        GatewayStatusUi.Failed(e.message ?: e::class.simpleName ?: "unknown error")
    }
}
