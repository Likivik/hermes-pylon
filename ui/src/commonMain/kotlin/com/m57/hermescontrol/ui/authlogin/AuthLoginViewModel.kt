// Modified from Hy4ri/hermes-mobile for this fork; see NOTICE.

package com.m57.hermescontrol.ui.authlogin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.m57.hermescontrol.data.config.ConnectionProfile
import com.m57.hermescontrol.data.local.AuthSessionState
import com.m57.hermescontrol.data.local.authStore
import com.m57.hermescontrol.data.remote.ApiClient
import com.m57.hermescontrol.data.remote.AuthPayloads
import com.m57.hermescontrol.data.remote.HermesHttpConfig
import com.m57.hermescontrol.data.remote.ServerEndpoint
import com.m57.hermescontrol.data.remote.createHermesHttpClient
import com.m57.hermescontrol.data.remote.parseForBuild
import com.m57.hermescontrol.data.remote.safeApiCall
import com.m57.hermescontrol.data.ws.HermesWsClient
import com.m57.hermescontrol.generated.resources.Res
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
    val loggedInProfiles: List<ConnectionProfile> = emptyList(),
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
                baseUrl = authStore.getBaseUrl(),
            ),
        )
    val uiState: StateFlow<AuthLoginUiState> = _uiState.asStateFlow()

    init {
        loadLoggedInProfiles()
    }

    fun loadLoggedInProfiles() {
        val allProfiles = authStore.getConnectionProfiles()
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
        ApiClient.rebuild()
        if (profileChanged) {
            HermesWsClient.connect()
        }
        _uiState.update { it.copy(connectionSuccess = true) }
    }

    companion object {
        private const val TAG = "AuthLoginVM"
    }

    private val probeClient: HttpClient = dashboardProbeClient

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

        viewModelScope.launch {
            val result =
                withContext(ioDispatcher) {
                    probeDashboardInternal(endpoint)
                }
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
                    errorMessage = unreachableMsg,
                )
            }
        }
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
     * Step 2: Connect using the detected auth mode.
     */
    fun connect() {
        val state = _uiState.value
        val endpoint =
            try {
                ServerEndpoint.parseForBuild(state.baseUrl)
            } catch (e: IllegalArgumentException) {
                _uiState.update { it.copy(errorMessage = e.message) }
                return
            }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

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
                // The validated login may replace another profile or endpoint.
                // Stop the old transport before mutating application auth state.
                HermesWsClient.disconnect(clearPendingMessages = true)
                AuthSessionState.markAuthenticated()
                authStore.setBaseUrl(endpoint.baseUrl.toString())
                if (state.authMode == DashboardAuthMode.TOKEN_ONLY) {
                    authStore.setToken(result.loopbackToken)
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
                ApiClient.rebuild()
                // A previous failed session may have left the singleton in
                // AUTH_EXPIRED, which connect() intentionally refuses to clear.
                HermesWsClient.connect()
                _uiState.update { it.copy(isLoading = false, connectionSuccess = true) }
            }
        }
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
            ApiClient.tempService(
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
