package com.m57.hermescontrol.ui.diagnostics

import com.m57.hermescontrol.data.diag.NetworkEvent
import com.m57.hermescontrol.data.diag.NetworkLog
import com.m57.hermescontrol.data.local.authStore
import com.m57.hermescontrol.data.remote.GatewayPlatform
import com.m57.hermescontrol.data.remote.HermesHttpConfig
import com.m57.hermescontrol.data.remote.createHermesHttpClient
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

/**
 * One probe and what it means.
 *
 * [verdict] is the point of this screen: a status code alone told us nothing
 * when `/api/status` answered `401` from a shell and timed out from the app.
 * Every check therefore states the conclusion, not just the number.
 */
data class DiagnosticCheck(
    val name: String,
    val outcome: String,
    val verdict: String,
    val healthy: Boolean,
    val durationMillis: Long,
)

data class DiagnosticsState(
    val environment: DiagnosticsEnvironment = DiagnosticsEnvironment(target = "unknown", origin = null),
    val endpoint: String = "…",
    val cleartext: Boolean = false,
    val securityWarning: String? = null,
    val storeBaseUrl: String? = null,
    val hasToken: Boolean = false,
    val selectedProfile: String? = null,
    val endpointError: String? = null,
    val checks: List<DiagnosticCheck> = emptyList(),
    val running: Boolean = false,
)

/**
 * Collects what the app knows about its own connection and lets the user
 * re-probe on demand.
 *
 * Deliberately not a `ViewModel`: the one screen that must work when the
 * lifecycle plumbing is what is broken should not depend on it.
 */
class DiagnosticsViewModel {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val probe = createHermesHttpClient(HermesHttpConfig.Probe)

    private val _state = MutableStateFlow(DiagnosticsState())
    val state: StateFlow<DiagnosticsState> = _state.asStateFlow()

    /** Every request the shared client made, newest last. */
    val events: StateFlow<List<NetworkEvent>> = NetworkLog.events

    init {
        refreshFacts()
    }

    fun refreshFacts() {
        val environment =
            runCatching { diagnosticsEnvironment() }.getOrElse {
                DiagnosticsEnvironment(target = "unavailable: ${it.message}", origin = null)
            }
        val storeFacts =
            runCatching {
                Triple(authStore.getBaseUrl(), authStore.getToken(), authStore.getSelectedProfileId())
            }.getOrNull()
        val endpoint = runCatching { GatewayPlatform.endpoint() }

        _state.update { current ->
            current.copy(
                environment = environment,
                endpoint = endpoint.map { it.baseUrl.toString() }.getOrElse { current.endpoint },
                cleartext = endpoint.map { !it.baseUrl.isHttps }.getOrElse { false },
                securityWarning = endpoint.getOrNull()?.securityWarning,
                endpointError = endpoint.exceptionOrNull()?.let { e -> "${e::class.simpleName}: ${e.message}" },
                storeBaseUrl = storeFacts?.first,
                hasToken = !storeFacts?.second.isNullOrBlank(),
                selectedProfile = storeFacts?.third,
            )
        }
    }

    fun runChecks() {
        if (_state.value.running) return
        _state.update { it.copy(running = true, checks = emptyList()) }
        scope.launch {
            refreshFacts()
            val endpoint = runCatching { GatewayPlatform.endpoint() }.getOrNull()
            if (endpoint == null) {
                _state.update { current ->
                    current.copy(
                        running = false,
                        checks =
                            listOf(
                                DiagnosticCheck(
                                    name = "endpoint",
                                    outcome = current.endpointError ?: "unresolved",
                                    verdict =
                                        "The app cannot build a gateway URL at all — fix the endpoint " +
                                            "before reading anything else.",
                                    healthy = false,
                                    durationMillis = 0,
                                ),
                            ),
                    )
                }
                return@launch
            }

            val checks = mutableListOf<DiagnosticCheck>()
            checks += statusCheck(endpoint.baseUrl.resolve("api/status").toString())
            checks += originCheck(endpoint.baseUrl.toString())
            checks += loginPageCheck(endpoint.baseUrl.resolve("login").toString())
            _state.update { it.copy(running = false, checks = checks) }
        }
    }

    private suspend fun statusCheck(url: String): DiagnosticCheck {
        val mark = TimeSource.Monotonic.markNow()
        return try {
            val response = probe.get(url)
            val code = response.status.value
            val (verdict, healthy) =
                when {
                    code in 200..299 ->
                        "Gateway reachable and open (no auth gate)." to true
                    code == 401 || code == 403 ->
                        "Gateway reachable; it wants credentials. A 401 here is normal before sign-in." to true
                    code == 404 ->
                        "Reached a server that is not the gateway — this origin serves something else. " +
                            "Wrong port or proxy target." to false
                    code in 500..599 ->
                        "Gateway reachable but failing ($code) — look at the gateway process, not the client." to false
                    else -> "Gateway answered $code; unexpected for /api/status." to false
                }
            DiagnosticCheck("GET /api/status", "HTTP $code", verdict, healthy, mark.elapsedNow().inWholeMilliseconds)
        } catch (t: Throwable) {
            DiagnosticCheck(
                name = "GET /api/status",
                outcome = t::class.simpleName ?: "failure",
                verdict =
                    "No response at all: the request never completed. Transport, not the app — check the " +
                        "reverse proxy, the tailnet path, and whether the process is listening.",
                healthy = false,
                durationMillis = mark.elapsedNow().inWholeMilliseconds,
            )
        }
    }

    private suspend fun originCheck(url: String): DiagnosticCheck {
        val mark = TimeSource.Monotonic.markNow()
        return try {
            val response = probe.get(url)
            val location = response.headers[HttpHeaders.Location]
            val code = response.status.value
            val (verdict, healthy) =
                when {
                    code == 302 && location?.contains("/login", ignoreCase = true) == true ->
                        "Origin redirects to /login — the gateway is gated by a session (basic auth mode)." to true
                    code in 200..299 ->
                        "Origin serves a page directly; auth is token-only or unguarded." to true
                    else -> "Origin answered $code — not the redirect this app expects." to false
                }
            DiagnosticCheck(
                "GET / (auth mode probe)",
                listOfNotNull("HTTP $code", location?.let { "→ $it" }).joinToString(" "),
                verdict,
                healthy,
                mark.elapsedNow().inWholeMilliseconds,
            )
        } catch (t: Throwable) {
            DiagnosticCheck(
                "GET / (auth mode probe)",
                t::class.simpleName ?: "failure",
                "Origin unreachable while /api/status may have worked — the proxy routes paths differently.",
                false,
                mark.elapsedNow().inWholeMilliseconds,
            )
        }
    }

    private suspend fun loginPageCheck(url: String): DiagnosticCheck {
        val mark = TimeSource.Monotonic.markNow()
        return try {
            val response = probe.get(url)
            val code = response.status.value
            val server = response.headers[HttpHeaders.Server]
            val verdict =
                when {
                    server?.contains("HermesWebUI", ignoreCase = true) == true ->
                        "A Hermes web UI answers here ($server). If the app was sent to a web UI login page instead of its own, this is why."
                    code in 200..299 -> "A login page answers at this path."
                    else -> "No login page at this path ($code)."
                }
            DiagnosticCheck(
                "GET /login (who answers)",
                listOfNotNull("HTTP $code", server).joinToString(" "),
                verdict,
                true,
                mark.elapsedNow().inWholeMilliseconds,
            )
        } catch (t: Throwable) {
            DiagnosticCheck(
                "GET /login (who answers)",
                t::class.simpleName ?: "failure",
                "Login path unreachable.",
                false,
                mark.elapsedNow().inWholeMilliseconds,
            )
        }
    }
}
