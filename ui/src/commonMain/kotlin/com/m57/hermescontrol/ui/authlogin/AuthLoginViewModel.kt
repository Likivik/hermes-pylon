// Modified from Hy4ri/hermes-mobile for this fork; see NOTICE.

package com.m57.hermescontrol.ui.authlogin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.m57.hermescontrol.data.config.GatewayInstance
import com.m57.hermescontrol.data.local.AuthSessionState
import com.m57.hermescontrol.data.local.authStore
import com.m57.hermescontrol.data.local.defaultServerBaseUrl
import com.m57.hermescontrol.data.local.serverBaseUrlIsPageOrigin
import com.m57.hermescontrol.data.remote.AuthPayloads
import com.m57.hermescontrol.data.remote.GatewayConnection
import com.m57.hermescontrol.data.remote.HermesHttpConfig
import com.m57.hermescontrol.data.remote.ServerEndpoint
import com.m57.hermescontrol.data.remote.createHermesHttpClient
import com.m57.hermescontrol.data.remote.parseForBuild
import com.m57.hermescontrol.data.remote.safeApiCall
import com.m57.hermescontrol.data.ws.HermesWsClient
import com.m57.hermescontrol.generated.resources.Res
import com.m57.hermescontrol.generated.resources.auth_login_error_cross_origin
import com.m57.hermescontrol.generated.resources.auth_login_error_password_required
import com.m57.hermescontrol.generated.resources.auth_login_error_token_required
import com.m57.hermescontrol.generated.resources.auth_login_error_unreachable
import com.m57.hermescontrol.generated.resources.auth_login_error_username_required
import com.m57.hermescontrol.generated.resources.connect_error_401
import com.m57.hermescontrol.generated.resources.connect_error_403
import com.m57.hermescontrol.generated.resources.connect_error_connection_failed
import com.m57.hermescontrol.generated.resources.connect_error_http_code
import com.m57.hermescontrol.ui.common.StringResolver
import com.m57.hermescontrol.util.HermesLog
import com.m57.hermescontrol.util.ioDispatcher
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.reflect.KClass

/**
 * What auth the dashboard requires.
 */
enum class DashboardAuthMode {
    /** Dashboard has no auth gate — just needs a session token. */
    TOKEN_ONLY,

    /** Dashboard has basic auth — needs username + password (and possibly a token). */
    BASIC_AUTH,

    /** Dashboard requires both basic auth credentials and a session token. */
    ALL,
}

data class AuthLoginUiState(
    val baseUrl: String = ServerEndpoint.DEFAULT_BASE_URL,
    val transportWarning: String? = null,
    val token: String = "",
    val username: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val probing: Boolean = false,
    val authMode: DashboardAuthMode? = null,
    val connectionSuccess: Boolean = false,
    val errorMessage: String? = null,
    /** A Nous sign-in problem, resolved to text by the screen. Null means no Nous error. */
    val nousError: NousLoginError? = null,
    /**
     * Interactive sign-in providers the gateway advertises, by name. Fetched during the probe so
     * the browser button appears only where the gateway can actually start that sign-in.
     */
    val providers: List<String> = emptyList(),
    val loggedInProfiles: List<GatewayInstance> = emptyList(),
)

/**
 * Probe / ticket-minting client, shared by every instance.
 *
 * Same short timeouts as the retired `OkHttpProvider.probe` (5 s connect, read
 * and write), and one instance for the process: an [HttpClient] is expensive to
 * build and safe to share. It is intentionally never closed — the app lifetime
 * is the client lifetime.
 */
private val dashboardProbeClient: HttpClient by lazy { createHermesHttpClient(HermesHttpConfig.Probe) }

class AuthLoginViewModel(
    private val resolver: StringResolver,
) : ViewModel() {
    private val _uiState =
        MutableStateFlow(
            AuthLoginUiState(
                baseUrl = initialBaseUrl(),
            ),
        )
    val uiState: StateFlow<AuthLoginUiState> = _uiState.asStateFlow()

    init {
        loadLoggedInProfiles()
        restoreNativeSession()
    }

    /**
     * Adopts a stored native token at launch, so a Nous sign-in survives the process ending.
     *
     * Runs silently: a stale or rejected token clears itself and leaves the login screen as the
     * user left it. A network call is made only when something is actually stored, so password
     * users pay nothing for this.
     */
    private fun restoreNativeSession() {
        val store = com.m57.hermescontrol.data.local.AuthManager.secretStoreOrNull() ?: return
        val endpoint = runCatching { ServerEndpoint.parseForBuild(_uiState.value.baseUrl) }.getOrNull() ?: return
        val session =
            com.m57.hermescontrol.data.local.NativeSession(
                exchange = NativeTokenExchange(com.m57.hermescontrol.data.remote.createHermesHttpClient()),
                store = com.m57.hermescontrol.data.local.NativeTokenStore(store),
            )
        if (session.stored() == null) return

        viewModelScope.launch {
            val token = session.current(endpoint.baseUrl.toString())
            if (token == null) return@launch

            val validated = connectTokenOnly(endpoint, token.accessToken)
            if (validated != null) {
                completeSignIn(endpoint, DashboardAuthMode.TOKEN_ONLY, validated)
            } else {
                // The stored token is dead. Clear it and drop the error it raised so the screen
                // still looks like a normal first launch.
                session.forget()
                _uiState.update { it.copy(isLoading = false, errorMessage = null) }
            }
        }
    }

    fun loadLoggedInProfiles() {
        val allProfiles = authStore.getGatewayInstances()
        val loggedIn =
            allProfiles.filter { profile ->
                val token = authStore.getProfileToken(profile.id)
                !token.isNullOrBlank()
            }
        _uiState.update { it.copy(loggedInProfiles = loggedIn) }
    }

    fun useExistingProfile(profileId: String) {
        if (AuthSessionState.signInRequired.value) return

        val profileChanged = profileId != authStore.getSelectedProfileId()
        if (profileChanged) {
            HermesWsClient.disconnect(clearPendingMessages = true)
        }
        authStore.setSelectedProfileId(profileId)
        GatewayConnection.rebuild()
        if (profileChanged) {
            HermesWsClient.connect()
        }
        _uiState.update { it.copy(connectionSuccess = true) }
    }

    companion object {
        private const val TAG = "AuthLoginVM"

        /** A blocked or hung probe must fail, not spin. */
        private const val PROBE_TIMEOUT_MILLIS = 15_000L

        /** Login POSTs, including basic-auth validation, get longer than a probe. */
        private const val CONNECT_TIMEOUT_MILLIS = 25_000L
    }

    private val probeClient: HttpClient = dashboardProbeClient

    /**
     * A browser can only reach its own origin, so an edited URL in the web build
     * is not a preference to honour silently — it is a request the browser will
     * refuse. Say so at the field, not as "unreachable" after a failed fetch.
     *
     * Cross-origin would work if the dashboard sent `Access-Control-Allow-Origin`;
     * that is tracked as a feature request, and this message is the gate.
     */
    private suspend fun crossOriginMessage(candidate: String): String? {
        if (!serverBaseUrlIsPageOrigin || candidate.isBlank()) return null
        val sameOrigin =
            runCatching {
                val parsed = ServerEndpoint.parseForBuild(candidate).baseUrl
                val expected = ServerEndpoint.parseForBuild(defaultServerBaseUrl).baseUrl
                parsed.protocol == expected.protocol &&
                    parsed.host == expected.host &&
                    parsed.port == expected.port
            }.getOrDefault(false)
        return if (sameOrigin) null else resolver.get(Res.string.auth_login_error_cross_origin, defaultServerBaseUrl)
    }

    /**
     * A browser cannot reach any origin but its own, so a stored value is ignored
     * there. Restoring it is what made the web login sit on a saved `:9119` URL
     * that no phone can reach.
     */
    private fun initialBaseUrl(): String =
        if (serverBaseUrlIsPageOrigin) defaultServerBaseUrl else authStore.getBaseUrl()

    fun onBaseUrlChange(value: String) {
        val warning =
            runCatching {
                ServerEndpoint.parseForBuild(value).securityWarning
            }.getOrNull()
        _uiState.update {
            it.copy(
                baseUrl = value.trim(),
                transportWarning = warning,
                errorMessage = null,
                authMode = null,
            )
        }
        if (serverBaseUrlIsPageOrigin) {
            viewModelScope.launch {
                val message = crossOriginMessage(value.trim())
                _uiState.update { state ->
                    if (state.baseUrl == value.trim()) state.copy(errorMessage = message) else state
                }
            }
        }
    }

    /** Reset ephemeral connection state when the screen leaves composition. */
    fun clearConnectionState() {
        _uiState.update {
            it.copy(
                connectionSuccess = false,
                errorMessage = null,
                isLoading = false,
            )
        }
    }

    fun onTokenChange(value: String) {
        _uiState.update { it.copy(token = value.trim(), errorMessage = null) }
    }

    fun onUsernameChange(value: String) {
        _uiState.update { it.copy(username = value.trim(), errorMessage = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, errorMessage = null) }
    }

    /**
     * Step 1: Probe the dashboard to detect what auth it needs.
     */
    fun probe() {
        val state = _uiState.value
        val endpoint =
            try {
                ServerEndpoint.parseForBuild(state.baseUrl)
            } catch (e: IllegalArgumentException) {
                _uiState.update { it.copy(errorMessage = e.message) }
                return
            }

        _uiState.update {
            it.copy(
                probing = true,
                errorMessage = null,
                authMode = null,
                baseUrl = endpoint.baseUrl.toString(),
                transportWarning = endpoint.securityWarning,
            )
        }

        // The deadline is a watchdog, not `withTimeoutOrNull` around the call.
        // A fetch stuck in a browser-level connect (cross-origin to a filtered
        // port) never delivers cancellation, so a timeout wrapped around it
        // never resumes and the button spins forever. The watchdog only touches
        // UI state, so it fires whatever the request is doing.
        viewModelScope.launch {
            delay(PROBE_TIMEOUT_MILLIS)
            val timedOut = resolver.get(Res.string.auth_login_error_unreachable)
            _uiState.update { state ->
                if (!state.probing) {
                    state
                } else {
                    state.copy(probing = false, errorMessage = state.errorMessage ?: timedOut)
                }
            }
        }

        viewModelScope.launch {
            val result = withContext(ioDispatcher) { probeDashboardInternal(endpoint) }
            val providers = withContext(ioDispatcher) { fetchProviders(endpoint) }
            val unreachableMsg =
                if (result == null) {
                    resolver.get(Res.string.auth_login_error_unreachable)
                } else {
                    null
                }
            _uiState.update {
                it.copy(
                    probing = false,
                    authMode = result?.authMode,
                    token = result?.extractedToken ?: it.token,
                    providers = providers,
                    errorMessage = unreachableMsg,
                )
            }
        }
    }

    /**
     * The interactive providers the gateway advertises, by name.
     *
     * `/api/auth/providers` answers `503` when the deployment has no interactive provider at all.
     * That is a correct answer, not an outage, and an empty list is the safe reading: no browser
     * sign-in is offered. Failing closed matters here because the alternative is sending the user
     * to a provider round trip that 404s on arrival.
     */
    private suspend fun fetchProviders(endpoint: ServerEndpoint): List<String> =
        try {
            val response = probeClient.get(endpoint.baseUrl.resolve("api/auth/providers").toString())
            if (response.status.value !in 200..299) {
                emptyList()
            } else {
                WebSignIn.providerNames(response.bodyAsText())
            }
        } catch (e: Exception) {
            HermesLog.w(TAG, "Provider list fetch failed (${e::class.simpleName})")
            emptyList()
        }

    /**
     * Result of probing the dashboard.
     */
    private data class ProbeResult(
        val authMode: DashboardAuthMode,
        val extractedToken: String? = null,
    )

    /**
     * Probes the dashboard to determine [DashboardAuthMode].
     * Returns null if the dashboard is unreachable.
     * When [ProbeResult.extractedToken] is non-null, the session token
     * was found embedded in the dashboard SPA HTML and can be auto-populated.
     */
    private suspend fun probeDashboardInternal(endpoint: ServerEndpoint): ProbeResult? {
        val base = endpoint.baseUrl.toString()

        // Step 1: Check if dashboard is reachable via /api/status (always public)
        val statusOk =
            try {
                probeClient.get(endpoint.baseUrl.resolve("api/status").toString()).status.isSuccess()
            } catch (e: Exception) {
                HermesLog.w(TAG, "Status probe failed (${e::class.simpleName})")
                return null // Dashboard unreachable
            }

        if (!statusOk) return null

        // Step 2: Probe / to see if it redirects to /login (basic auth) or returns SPA
        val needsBasicAuth =
            try {
                val response = probeClient.get(base)
                val location = response.headers[HttpHeaders.Location]
                response.status.value == 302 &&
                    location?.contains("/login", ignoreCase = true) == true
            } catch (e: Exception) {
                HermesLog.w(TAG, "SPA probe failed (${e::class.simpleName})")
                false
            }

        // Step 3: Extract token from SPA HTML (if reachable without redirect)
        var extractedToken: String? = null
        if (!needsBasicAuth) {
            try {
                val body = probeClient.get(base).bodyAsText()
                // Extract __HERMES_SESSION_TOKEN__ from the SPA HTML
                val tokenMatch = Regex("""__HERMES_SESSION_TOKEN__\s*=\s*"([^"]+)"""").find(body)
                extractedToken = tokenMatch?.groupValues?.getOrNull(1)
                if (extractedToken == null) {
                    HermesLog.w(TAG, "SPA has no __HERMES_SESSION_TOKEN__ — mode might require both auth")
                }
            } catch (e: Exception) {
                HermesLog.w(TAG, "SPA token extraction failed (${e::class.simpleName})")
            }
        }

        val authMode =
            if (needsBasicAuth) {
                DashboardAuthMode.BASIC_AUTH
            } else if (extractedToken != null) {
                DashboardAuthMode.TOKEN_ONLY
            } else {
                DashboardAuthMode.ALL
            }

        return ProbeResult(authMode = authMode, extractedToken = extractedToken)
    }

    /**
     * Result of a successful connect attempt.
     */
    private data class ConnectResult(
        /** Persistent loopback token; gated mode mints a ticket per handshake. */
        val loopbackToken: String? = null,
    )

    /**
     * Continue with Nous: run the gateway's native flow, then adopt the bearer token.
     *
     * The token goes into the same encrypted store as the session token, so a later launch finds
     * exactly one credential. A cancelled tab is not an error — the user closed it, and an error
     * banner would be a lie about what happened.
     */
    fun signInWithNous(openBrowser: (String) -> Unit) {
        val store = com.m57.hermescontrol.data.local.AuthManager.secretStoreOrNull()
        val endpoint = runCatching { ServerEndpoint.parseForBuild(_uiState.value.baseUrl) }.getOrNull()
        if (store == null || endpoint == null) {
            _uiState.update {
                it.copy(isLoading = false, nousError = NousLoginError(NousLoginErrorKind.Unavailable))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val signIn =
                NativeSignIn(
                    exchange = NativeTokenExchange(com.m57.hermescontrol.data.remote.createHermesHttpClient()),
                    store = com.m57.hermescontrol.data.local.NativeTokenStore(store),
                )

            when (val result = signIn.signIn(endpoint.baseUrl.toString(), openBrowser = openBrowser)) {
                is NativeSignInResult.Success -> {
                    // connectTokenOnly returns the token when the gateway accepts it, and null
                    // after setting errorMessage when it does not. Reading its return as an error
                    // string inverts both outcomes: the token becomes a visible error and success
                    // never advances the screen.
                    val validated = connectTokenOnly(endpoint, result.token.accessToken)
                    if (validated != null) {
                        completeSignIn(endpoint, DashboardAuthMode.TOKEN_ONLY, validated)
                    }
                }
                is NativeSignInResult.Cancelled -> {
                    _uiState.update { it.copy(isLoading = false, errorMessage = null, nousError = null) }
                }
                is NativeSignInResult.Failed -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = null,
                            nousError = NousLoginError.of(result.failure, result.detail),
                        )
                    }
                }
                is NativeSignInResult.Unsupported -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = null,
                            nousError = NousLoginError(NousLoginErrorKind.Unsupported),
                        )
                    }
                }
            }
        }
    }

    /**
     * Starts the browser flow instead of the native one.
     *
     * Whole-page navigation, not a fetch: the gateway has to set its cookie on its own origin, and
     * only a top-level navigation does that. Nothing is stored by Pylon — the cookie is the
     * credential, and it never becomes visible to this app.
     */
    fun signInWithNousInBrowser(navigate: (String) -> Unit) {
        val endpoint = runCatching { ServerEndpoint.parseForBuild(_uiState.value.baseUrl) }.getOrNull()
        if (endpoint == null) {
            _uiState.update { it.copy(nousError = NousLoginError(NousLoginErrorKind.Unavailable)) }
            return
        }

        _uiState.update { it.copy(errorMessage = null, nousError = null) }
        navigate(WebSignIn.authorizeUrl(endpoint.baseUrl.toString()))
    }

    /**
     * Step 2: Connect using the detected auth mode.
     */
    fun connect() {
        if (serverBaseUrlIsPageOrigin) {
            viewModelScope.launch {
                val message = crossOriginMessage(_uiState.value.baseUrl)
                if (message == null) {
                    connectSameOrigin()
                } else {
                    _uiState.update { it.copy(isLoading = false, probing = false, errorMessage = message) }
                }
            }
            return
        }
        connectSameOrigin()
    }

    private fun connectSameOrigin() {
        val state = _uiState.value
        val endpoint =
            try {
                ServerEndpoint.parseForBuild(state.baseUrl)
            } catch (e: IllegalArgumentException) {
                _uiState.update { it.copy(errorMessage = e.message) }
                return
            }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        // Same trap as the probe (see `probe`): a request stuck in a
        // browser-level connect never delivers cancellation, so the deadline is
        // a watchdog that only touches state. Without it a hung POST leaves the
        // Connect button disabled forever, which on wasmJs is the visible
        // difference between "bad password" and "this app is broken".
        viewModelScope.launch {
            delay(CONNECT_TIMEOUT_MILLIS)
            val timedOut = resolver.get(Res.string.auth_login_error_unreachable)
            _uiState.update { current ->
                if (!current.isLoading) {
                    current
                } else {
                    current.copy(isLoading = false, errorMessage = current.errorMessage ?: timedOut)
                }
            }
        }

        viewModelScope.launch {
            val result =
                withContext(ioDispatcher) {
                    when (state.authMode) {
                        DashboardAuthMode.TOKEN_ONLY -> {
                            val token = connectTokenOnly(endpoint, state.token)
                            if (token != null) ConnectResult(loopbackToken = token) else null
                        }

                        DashboardAuthMode.BASIC_AUTH -> {
                            connectBasicAuth(endpoint, state.username, state.password)
                        }

                        DashboardAuthMode.ALL -> {
                            connectBasicAuth(endpoint, state.username, state.password)
                        }

                        null -> {
                            null
                        }
                    }
                }

            if (result != null) {
                completeSignIn(endpoint, state.authMode, result.loopbackToken)
            }
        }
    }

    /**
     * Everything that must happen once a credential has been validated.
     *
     * Extracted from [connect] rather than copied: two sign-in paths each carrying their own copy
     * of this sequence is how one of them ends up without `markAuthenticated()` or without a
     * rebuilt transport, and the symptom is a login that "works" but never leaves the screen.
     */
    private fun completeSignIn(
        endpoint: ServerEndpoint,
        authMode: DashboardAuthMode?,
        loopbackToken: String?,
    ) {
        // The validated login may replace another profile or endpoint.
        // Stop the old transport before mutating application auth state.
        HermesWsClient.disconnect(clearPendingMessages = true)
        AuthSessionState.markAuthenticated()
        authStore.setBaseUrl(endpoint.baseUrl.toString())
        if (authMode == DashboardAuthMode.TOKEN_ONLY) {
            authStore.setToken(loopbackToken)
            // Loopback mode — no session cookie; ensure any stale one
            // is cleared so the jar only sends the Bearer token.
            authStore.setSessionCookie(null)
            authStore.setWsAuthParam("token")
        } else {
            // Gated (BASIC_AUTH / ALL): the session cookie was captured
            // automatically by the shared CookieJar during the login
            // call (issue #470), so we keep it and switch the WS auth
            // param to a fresh, handshake-local ticket.
            authStore.setToken(null)
            authStore.setWsAuthParam("ticket")
        }
        GatewayConnection.rebuild()
        // A previous failed session may have left the singleton in
        // AUTH_EXPIRED, which connect() intentionally refuses to clear.
        HermesWsClient.connect()
        _uiState.update { it.copy(isLoading = false, errorMessage = null, connectionSuccess = true) }
    }

    /**
     * Validate the token by calling /api/status with it.
     */
    private suspend fun connectTokenOnly(
        endpoint: ServerEndpoint,
        token: String,
    ): String? {
        if (token.isBlank()) {
            val msg = resolver.get(Res.string.auth_login_error_token_required)
            _uiState.update {
                it.copy(isLoading = false, errorMessage = msg)
            }
            return null
        }

        val tempApi =
            GatewayConnection.tempService(
                endpoint.baseUrl.toString(),
                token,
            )
        val result = safeApiCall(reportAuthExpiry = false) { tempApi.getSessions() }

        return when (result) {
            is com.m57.hermescontrol.data.remote.NetworkResult.Success -> {
                token
            }

            is com.m57.hermescontrol.data.remote.NetworkResult.Failure -> {
                val msg =
                    when (val err = result.error) {
                        is com.m57.hermescontrol.data.remote.NetworkError.Http -> {
                            when (err.code) {
                                401 -> resolver.get(Res.string.connect_error_401)
                                403 -> resolver.get(Res.string.connect_error_403)
                                else -> resolver.get(Res.string.connect_error_http_code, err.code)
                            }
                        }

                        is com.m57.hermescontrol.data.remote.NetworkError.AuthExpired -> {
                            resolver.get(Res.string.connect_error_401)
                        }

                        is com.m57.hermescontrol.data.remote.NetworkError.Connection -> {
                            resolver.get(Res.string.connect_error_connection_failed, err.cause.message ?: "")
                        }

                        is com.m57.hermescontrol.data.remote.NetworkError.Unknown -> {
                            resolver.get(Res.string.connect_error_connection_failed, err.cause.message ?: "")
                        }
                    }
                _uiState.update { it.copy(isLoading = false, errorMessage = msg) }
                null
            }
        }
    }

    /**
     * Authenticate with basic auth. The shared CookieJar captures the dashboard
     * session; [HermesWsClient] mints a one-use ticket for each handshake.
     */
    private suspend fun connectBasicAuth(
        endpoint: ServerEndpoint,
        username: String,
        password: String,
    ): ConnectResult? {
        if (username.isBlank()) {
            val msg = resolver.get(Res.string.auth_login_error_username_required)
            _uiState.update {
                it.copy(isLoading = false, errorMessage = msg)
            }
            return null
        }
        if (password.isBlank()) {
            val msg = resolver.get(Res.string.auth_login_error_password_required)
            _uiState.update {
                it.copy(isLoading = false, errorMessage = msg)
            }
            return null
        }

        val jsonBody = AuthPayloads.passwordLogin(username, password)

        try {
            // Step 1: Authenticate via the password login endpoint. The platform
            // captures the session cookie this response carries (on Android the
            // persistent jar picks it up — issue #470).
            val statusCode = performPasswordLogin(endpoint, jsonBody)
            if (statusCode !in 200..299) {
                val msg =
                    when (statusCode) {
                        401 -> resolver.get(Res.string.connect_error_401)
                        403 -> resolver.get(Res.string.connect_error_403)
                        else ->
                            resolver.get(
                                Res.string.connect_error_http_code,
                                statusCode,
                            )
                    }
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = msg)
                }
                return null
            }

            return ConnectResult()
        } catch (e: Exception) {
            val msg = resolver.get(Res.string.connect_error_connection_failed, e.message ?: "")
            _uiState.update {
                it.copy(
                    isLoading = false,
                    errorMessage = msg,
                )
            }
            return null
        }
    }
}

class AuthLoginViewModelFactory(
    private val resolver: StringResolver,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: KClass<T>,
        extras: CreationExtras,
    ): T = AuthLoginViewModel(resolver) as T
}
