package com.m57.hermescontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.FontFamily
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
import com.m57.hermescontrol.generated.resources.landing_action_auth_login
import com.m57.hermescontrol.generated.resources.landing_subtitle
import com.m57.hermescontrol.generated.resources.landing_title
import com.m57.hermescontrol.theme.HermesControlTheme
import com.m57.hermescontrol.ui.diagnostics.DiagnosticsScreen
import com.m57.hermescontrol.ui.icons.MaterialSymbol
import com.m57.hermescontrol.ui.icons.MaterialSymbolPickerSection
import com.m57.hermescontrol.ui.icons.materialSymbolsSubsetFamily
import com.m57.hermescontrol.ui.landing.LandingScreen
import kotlinx.browser.document
import kotlinx.browser.window
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.skiko.wasm.onWasmReady
import kotlin.js.js

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
        // Console sink for DevTools; the ring is always on inside HermesLog, so
        // diagnostics work even if this install never runs.
        com.m57.hermescontrol.util.HermesLog.setDebugLogging(com.m57.hermescontrol.BuildFlags.isDebug)
        // The app needs the manager before the first composition reads its
        // flows; the browser platform persists profiles in `localStorage` and
        // keeps secrets and cookies in memory for the life of the tab.
        com.m57.hermescontrol.data.local.AuthManager.init(
            com.m57.hermescontrol.data.local.WebAuthPlatform(),
        )
        adoptVerifiedSession()
        ComposeViewport(viewportContainer = document.body!!) {
            HermesControlTheme(themePreference = ThemePreference.DARK) {
                // The browser shell draws a branded loading screen and removes it
                // on this signal. Removing it when the container appears is too
                // early: Compose has a container but has painted nothing, so the
                // user sees the landing layout with blank labels for a moment.
                // Two frames means composition is done *and* a frame was drawn.
                val fontResolver = LocalFontFamilyResolver.current
                // Created in composition (Font(...) is @Composable) so the ~200 KB
                // subset starts fetching immediately; the gate below then waits
                // for it, guaranteeing rail/drawer glyphs never paint as tofu
                // after the overlay leaves. The full 10.7 MB font is deliberately
                // NOT awaited — it is fetched on demand for out-of-subset glyphs.
                val iconSubsetFamily = materialSymbolsSubsetFamily(filled = true)
                LaunchedEffect(Unit) {
                    withFrameNanos { }
                    withFrameNanos { }
                    // The overlay must not leave until there is something to read.
                    // Two separate waits, in order of what the user notices:
                    //   1. strings — a resource file fetched by the runtime; until
                    //      it lands every `stringResource` is unresolved, so the
                    //      hero renders as shapes with no labels at all;
                    //   2. the default font — glyphs resolve asynchronously too.
                    // Awaiting the font alone was not enough: the overlay left
                    // ~0.8 s before any text existed.
                    runCatching {
                        getString(Res.string.landing_title)
                        getString(Res.string.landing_subtitle)
                        getString(Res.string.landing_action_auth_login)
                    }
                    runCatching { fontResolver.preload(FontFamily.Default) }
                    runCatching { fontResolver.preload(iconSubsetFamily) }
                    withFrameNanos { }
                    notifyBrowserShellReady()
                }
                // `?icons` still renders the icon-picker proof surface; every
                // other URL is the real app.
                if (window.location.search.contains("icons")) {
                    PylonWebEntry()
                } else {
                    com.m57.hermescontrol.MainNavigation()
                }
            }
        }
    }
}

// Tells index.html that the first frame is on screen, so its loading overlay can
// go away at the right moment instead of on container creation. No-op when the
// shell is not the stock one (native builds never call this).
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun notifyBrowserShellReady() {
    js("typeof window.__pylonReady === 'function' && window.__pylonReady()")
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
    // A 401 anywhere in the app means the session is gone. On the web that must
    // leave for the HTML sign-in page rather than fall through to the Compose
    // one, which no password manager can fill.
    val sessionExpired by com.m57.hermescontrol.data.local.AuthSessionState.signInRequired.collectAsState()
    LaunchedEffect(sessionExpired) {
        if (sessionExpired) {
            com.m57.hermescontrol.ui.authlogin.handleSignOut(
                com.m57.hermescontrol.ui.authlogin.SignOutReason.SESSION_EXPIRED,
            )
        }
    }
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
    var showDiagnostics by remember { mutableStateOf(false) }

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

        // Diagnostics is a permanent surface, not a debug leftover: the failure
        // that motivated it was only diagnosable by asking "what request did the
        // app actually make, and what came back?" from the device itself.
        OutlinedButton(
            onClick = { showDiagnostics = !showDiagnostics },
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
        ) {
            Text(if (showDiagnostics) "Close diagnostics" else "Diagnostics")
        }

        if (showDiagnostics) {
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier.fillMaxSize(),
            ) {
                DiagnosticsScreen()
            }
        }
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

/**
 * Kotlin/Wasm only allows `js(...)` as a single expression in a top-level
 * function body, so the interop read lives here rather than inline.
 */
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
private fun pylonSessionState(): String = js("window.__pylonSession || 'unknown'")

/**
 * Adopt the session that `index.html` verified, and only that.
 *
 * The gate probes `api/profiles` before it loads this module and records the
 * answer on `window.__pylonSession`. Three outcomes matter here:
 *
 * - `ok` — the server confirmed a session a moment ago, so the app adopts it
 *   instead of asking for the password a second time.
 * - `error` — the server could not be asked. Nothing may be assumed: adopting
 *   here would invent a session, and every later request would 401 anyway.
 * - anything else — no adoption; the app's own auth handling takes over.
 *
 * This is the same three steps `connectBasicAuth` performs after a successful
 * POST, minus the POST: point the profile at the page origin, switch the
 * WebSocket to a handshake ticket (cookie auth, not a bearer token), and mark
 * the session authenticated. The cookie stays in the browser, so a reload
 * re-adopts it the same way.
 */
private fun adoptVerifiedSession() {
    if (pylonSessionState() != "ok") return
    runCatching {
        val manager = com.m57.hermescontrol.data.local.AuthManager
        val origin = window.location.origin
        // Only when unset: a profile pointed at another server keeps it.
        if (manager.getBaseUrl().isBlank()) manager.setBaseUrl(origin)
        manager.setWsAuthParam("ticket")

        // The sign-in page authenticates the browser, so it cannot register the
        // connection the way the Compose sign-in did — and Settings would open on
        // an empty Connections list. Naming the page origin as a profile is the
        // app-side half of adoption, and it lives here rather than on the HTML
        // page because only the app knows what a profile is.
        seedProfileForThisOrigin(manager, origin)

        com.m57.hermescontrol.data.local.AuthSessionState.markAuthenticated()
        com.m57.hermescontrol.data.remote.ApiClient.rebuild()
        com.m57.hermescontrol.data.ws.HermesWsClient.connect()
    }
}

/**
 * Make the page origin visible in Settings → Connections.
 *
 * Idempotent: an existing profile for this origin is left exactly as it is, and
 * an existing selection is kept. Only the "signed in to this web build and the
 * list is empty" case adds anything, which is every first visit through
 * `login.html`.
 */
private fun seedProfileForThisOrigin(
    manager: com.m57.hermescontrol.data.local.AuthManager,
    origin: String,
) {
    val existing = manager.getConnectionProfiles()
    val normalised = origin.trimEnd('/')
    val match = existing.firstOrNull { it.resolvedBaseUrl.trimEnd('/') == normalised }
    if (match != null) {
        if (manager.getSelectedProfileId() != match.id) {
            manager.saveConnectionProfilesAndSelect(existing, match.id, null)
        }
        return
    }
    // A clean store starts with one placeholder profile pointing at the build
    // default (127.0.0.1:9119). Appending beside it would leave the user with a
    // dead "Default" row they never created, so adopt it instead of adding to it.
    val pristine =
        existing.singleOrNull()?.takeIf {
            it.id == com.m57.hermescontrol.data.local.AuthManager.DEFAULT_PROFILE_ID &&
                it.resolvedBaseUrl.trimEnd('/') ==
                com.m57.hermescontrol.data.remote.ServerEndpoint.DEFAULT_BASE_URL.trimEnd('/')
        }
    if (pristine != null) {
        val adopted =
            pristine.copy(
                name = window.location.hostname.ifBlank { "Pylon" },
                baseUrl = origin,
                wsAuthParam = "ticket",
            )
        manager.saveConnectionProfilesAndSelect(listOf(adopted), adopted.id, null)
        return
    }

    val profile =
        com.m57.hermescontrol.data.config.ConnectionProfile(
            name = window.location.hostname.ifBlank { "Pylon" },
            baseUrl = origin,
            wsAuthParam = "ticket",
        )
    manager.saveConnectionProfilesAndSelect(existing + profile, profile.id, null)
}
