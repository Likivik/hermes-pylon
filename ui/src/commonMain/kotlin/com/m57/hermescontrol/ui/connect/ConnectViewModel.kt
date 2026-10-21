// Modified from Hy4ri/hermes-mobile for this fork; see NOTICE.

package com.m57.hermescontrol.ui.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m57.hermescontrol.AppEnvironment
import com.m57.hermescontrol.data.config.ConnectionProfile
import com.m57.hermescontrol.data.config.resolveBaseUrl
import com.m57.hermescontrol.data.local.AuthDefaults
import com.m57.hermescontrol.data.local.AuthSessionState
import com.m57.hermescontrol.data.local.authStore
import com.m57.hermescontrol.data.model.StatusResponse
import com.m57.hermescontrol.data.remote.ApiClient
import com.m57.hermescontrol.data.remote.CleartextPolicy
import com.m57.hermescontrol.data.remote.NetworkError
import com.m57.hermescontrol.data.remote.NetworkResult
import com.m57.hermescontrol.data.remote.ServerEndpoint
import com.m57.hermescontrol.data.remote.parseForBuild
import com.m57.hermescontrol.data.remote.safeApiCall
import com.m57.hermescontrol.generated.resources.Res
import com.m57.hermescontrol.generated.resources.connect_error_401
import com.m57.hermescontrol.generated.resources.connect_error_403
import com.m57.hermescontrol.generated.resources.connect_error_connection_failed
import com.m57.hermescontrol.generated.resources.connect_error_http_code
import com.m57.hermescontrol.generated.resources.connect_error_refused
import com.m57.hermescontrol.generated.resources.connect_error_resolve
import com.m57.hermescontrol.generated.resources.connect_error_timeout
import com.m57.hermescontrol.generated.resources.connect_error_token_required
import com.m57.hermescontrol.generated.resources.connect_error_url_invalid
import com.m57.hermescontrol.ui.common.StringResolver
import com.m57.hermescontrol.ui.common.reconcilePressureStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.m57.hermescontrol.util.ioDispatcher as ioDispatcher

data class ConnectUiState(
    val token: String = "",
    val baseUrl: String = ServerEndpoint.DEFAULT_BASE_URL,
    val transportWarning: String? = null,
    val isConnecting: Boolean = false,
    val connectionSuccess: Boolean = false,
    val errorMessage: String? = null,
    val profileName: String = "",
    val saveProfile: Boolean = false,
    val profiles: List<ConnectionProfile> = emptyList(),
    val selectedProfile: ConnectionProfile? = null,
    val authMode: String = "token",
    val status: StatusResponse? = null,
)

/**
 * ViewModel for the connect screen.
 *
 * Locale-aware error messages used to come from `Application.getString(Int)`.
 * They are now read through a [StringResolver] backed by Compose Resources,
 * so this VM no longer touches `Context` or `R.string` at all. The
 * resolver's `get` is `suspend`, so call sites that are not already
 * inside a coroutine (the `connect()` early-return validation paths)
 * spawn a small `viewModelScope.launch { ... }` to capture the text
 * before populating `_uiState`.
 */
class ConnectViewModel(
    private val resolver: StringResolver,
    private val ioDispatcher: CoroutineDispatcher = AppEnvironment.current.io,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ConnectUiState())
    val uiState: StateFlow<ConnectUiState> = _uiState.asStateFlow()
    private var statusGeneration = 0L

    init {
        loadSavedValues()
    }

    fun loadSavedValues() {
        val savedToken = authStore.getToken() ?: ""
        val savedBaseUrl = authStore.getBaseUrl()
        val profiles = authStore.getConnectionProfiles()
        val selectedId = authStore.getSelectedProfileId()
        val selectedProfile = profiles.firstOrNull { it.id == selectedId }
        val resolvedBaseUrl = selectedProfile?.resolveBaseUrl(savedBaseUrl) ?: savedBaseUrl
        val endpoint =
            ServerEndpoint.parse(
                resolvedBaseUrl,
                CleartextPolicy.ALLOW_WITH_WARNING,
            )
        _uiState.update {
            it.copy(
                token = savedToken,
                baseUrl = endpoint.baseUrl.toString(),
                transportWarning = endpoint.securityWarning,
                profiles = profiles,
                selectedProfile = selectedProfile,
                profileName = selectedProfile?.name ?: "",
                authMode = selectedProfile?.wsAuthParam?.takeIf(String::isNotBlank) ?: authStore.getWsAuthParam(),
                status = null,
            )
        }
        loadStatus()
    }

    fun onProfileNameChange(value: String) {
        _uiState.update { it.copy(profileName = value, errorMessage = null) }
    }

    fun onSaveProfileChange(value: Boolean) {
        _uiState.update { it.copy(saveProfile = value) }
    }

    fun selectProfile(profile: ConnectionProfile) {
        authStore.setSelectedProfileId(profile.id)
        val token = authStore.getProfileToken(profile.id) ?: ""
        val endpoint =
            ServerEndpoint.parse(
                profile.resolveBaseUrl(authStore.getBaseUrl()),
                CleartextPolicy.ALLOW_WITH_WARNING,
            )
        _uiState.update {
            it.copy(
                selectedProfile = profile,
                profileName = profile.name,
                baseUrl = endpoint.baseUrl.toString(),
                transportWarning = endpoint.securityWarning,
                token = token,
                authMode = profile.wsAuthParam?.takeIf(String::isNotBlank) ?: authStore.getWsAuthParam(),
                status = null,
            )
        }
        ApiClient.rebuild()
        loadStatus()
    }

    fun onTokenChange(value: String) {
        statusGeneration += 1
        _uiState.update { it.copy(token = value.trim(), errorMessage = null, status = null) }
    }

    fun onBaseUrlChange(value: String) {
        statusGeneration += 1
        val trimmed = value.trim()
        val warning =
            runCatching {
                ServerEndpoint.parse(trimmed, CleartextPolicy.ALLOW_WITH_WARNING).securityWarning
            }.getOrNull()
        _uiState.update { it.copy(baseUrl = trimmed, transportWarning = warning, errorMessage = null, status = null) }
    }

    /** Probe only the persisted selected profile, using its normal cookie-or-token client. */
    fun loadStatus() {
        val generation = ++statusGeneration
        val state = _uiState.value
        val profile = state.selectedProfile ?: return
        val selectedId = authStore.getSelectedProfileId() ?: return
        val expectedUrl = profile.resolveBaseUrl(authStore.getBaseUrl())
        val expectedToken = authStore.getProfileToken(profile.id).orEmpty()
        val expectedMode = profile.wsAuthParam?.takeIf(String::isNotBlank) ?: authStore.getWsAuthParam()
        if (profile.id != selectedId || state.baseUrl != expectedUrl || state.authMode != expectedMode ||
            (expectedMode != "ticket" && state.token != expectedToken)
        ) {
            return
        }
        val fingerprint = listOf(profile.id, state.baseUrl, state.token, state.authMode)
        viewModelScope.launch {
            val result =
                withContext(ioDispatcher) { safeApiCall(reportAuthExpiry = false) { ApiClient.hermesApi.getStatus() } }
            val current = _uiState.value
            val currentFingerprint =
                listOf(current.selectedProfile?.id.orEmpty(), current.baseUrl, current.token, current.authMode)
            if (generation != statusGeneration || fingerprint != currentFingerprint) return@launch
            when (result) {
                is NetworkResult.Success -> {
                    _uiState.update { currentState ->
                        currentState.copy(
                            status =
                                if (result.data.memory == null && result.data.disk == null) {
                                    null
                                } else {
                                    reconcilePressureStatus(currentState.status, result.data)
                                },
                        )
                    }
                }

                is NetworkResult.Failure -> _uiState.update { it.copy(status = null) }
            }
        }
    }

    fun connect() {
        val state = _uiState.value
        if (state.token.isBlank()) {
            // `resolver.get` is `suspend`; the early-return path is not
            // inside a coroutine, so we hoist into a one-shot launch
            // that captures the resolved text and then writes it into
            // the UI state.
            viewModelScope.launch {
                val msg = resolver.get(Res.string.connect_error_token_required)
                _uiState.update { it.copy(errorMessage = msg) }
            }
            return
        }
        val endpoint =
            runCatching { ServerEndpoint.parseForBuild(state.baseUrl) }.getOrNull()
        if (endpoint == null) {
            viewModelScope.launch {
                val msg = resolver.get(Res.string.connect_error_url_invalid)
                _uiState.update { it.copy(errorMessage = msg) }
            }
            return
        }

        val generation = ++statusGeneration
        val requestFingerprint =
            listOf(
                state.selectedProfile?.id.orEmpty(),
                state.baseUrl,
                state.token,
                state.authMode,
            )
        _uiState.update { it.copy(isConnecting = true, errorMessage = null, status = null) }

        viewModelScope.launch {
            val result =
                withContext(ioDispatcher) {
                    val tempApi = ApiClient.tempService(endpoint.baseUrl.toString(), state.token)
                    safeApiCall(reportAuthExpiry = false) { tempApi.getStatus() }
                }
            val current = _uiState.value
            val currentFingerprint =
                listOf(
                    current.selectedProfile?.id.orEmpty(),
                    current.baseUrl,
                    current.token,
                    current.authMode,
                )
            if (generation != statusGeneration || requestFingerprint != currentFingerprint) return@launch
            when (result) {
                is NetworkResult.Success -> {
                    // Persist credentials to the selected (Default) profile upon successful verification.
                    authStore.setToken(state.token)
                    authStore.setBaseUrl(state.baseUrl)
                    // Direct mode uses the bearer token for REST and WebSocket
                    // auth. Clear any gated-session state left by a previous
                    // endpoint before rebuilding the clients.
                    authStore.setSessionCookie(null)
                    authStore.setWsAuthParam("token")
                    ApiClient.rebuild()
                    AuthSessionState.markAuthenticated()

                    if (state.saveProfile) {
                        val currentProfiles = authStore.getConnectionProfiles()
                        val existingIndex =
                            currentProfiles.indexOfFirst {
                                it.name.equals(
                                    state.profileName,
                                    ignoreCase = true,
                                )
                            }
                        val targetProfile =
                            if (existingIndex >= 0) {
                                currentProfiles[existingIndex].copy(
                                    baseUrl = state.baseUrl,
                                    wsAuthParam = "token",
                                )
                            } else {
                                ConnectionProfile(
                                    name = state.profileName,
                                    baseUrl = state.baseUrl,
                                    wsAuthParam = "token",
                                )
                            }
                        val updatedProfiles =
                            if (existingIndex >= 0) {
                                currentProfiles.mapIndexed { idx, p -> if (idx == existingIndex) targetProfile else p }
                            } else {
                                currentProfiles + targetProfile
                            }
                        authStore.saveConnectionProfilesAndSelect(
                            updatedProfiles,
                            targetProfile.id,
                            state.token,
                        )
                    } else {
                        // No explicit profile name — store the connection on the default profile.
                        authStore.ensureDefaultSelected()
                        authStore.setSelectedProfileId(AuthDefaults.PROFILE_ID)
                        authStore.setProfileToken(AuthDefaults.PROFILE_ID, state.token)
                    }
                    ApiClient.rebuild()
                    _uiState.update {
                        it.copy(
                            isConnecting = false,
                            connectionSuccess = true,
                            errorMessage = null,
                            authMode = "token",
                            status =
                                result.data.takeIf { status ->
                                    status.memory != null || status.disk != null
                                },
                        )
                    }
                }

                is NetworkResult.Failure -> {
                    val msg =
                        when (val err = result.error) {
                            is NetworkError.Http -> {
                                when (err.code) {
                                    401 -> {
                                        authStore.setToken(null)
                                        if (authStore.getSelectedProfileId() != null) {
                                            authStore.setProfileToken(authStore.getSelectedProfileId()!!, null)
                                        }
                                        resolver.get(Res.string.connect_error_401)
                                    }

                                    403 -> {
                                        resolver.get(Res.string.connect_error_403)
                                    }

                                    else -> {
                                        resolver.get(Res.string.connect_error_http_code, err.code)
                                    }
                                }
                            }

                            is NetworkError.AuthExpired -> {
                                authStore.setToken(null)
                                if (authStore.getSelectedProfileId() != null) {
                                    authStore.setProfileToken(authStore.getSelectedProfileId()!!, null)
                                }
                                resolver.get(Res.string.connect_error_401)
                            }

                            is NetworkError.Connection -> {
                                val causeMessage = err.cause.message ?: ""
                                when {
                                    causeMessage.contains(
                                        "timeout",
                                        true,
                                    ) -> {
                                        resolver.get(Res.string.connect_error_timeout)
                                    }

                                    causeMessage.contains(
                                        "refused",
                                        true,
                                    ) -> {
                                        resolver.get(Res.string.connect_error_refused)
                                    }

                                    causeMessage.contains(
                                        "resolve",
                                        true,
                                    ) -> {
                                        resolver.get(Res.string.connect_error_resolve)
                                    }

                                    else -> {
                                        resolver.get(
                                            Res.string.connect_error_connection_failed,
                                            err.cause.message ?: "",
                                        )
                                    }
                                }
                            }

                            is NetworkError.Unknown -> {
                                val causeMessage = err.cause.message ?: ""
                                when {
                                    causeMessage.contains(
                                        "timeout",
                                        true,
                                    ) -> {
                                        resolver.get(Res.string.connect_error_timeout)
                                    }

                                    causeMessage.contains(
                                        "refused",
                                        true,
                                    ) -> {
                                        resolver.get(Res.string.connect_error_refused)
                                    }

                                    causeMessage.contains(
                                        "resolve",
                                        true,
                                    ) -> {
                                        resolver.get(Res.string.connect_error_resolve)
                                    }

                                    else -> {
                                        resolver.get(
                                            Res.string.connect_error_connection_failed,
                                            err.cause.message ?: "",
                                        )
                                    }
                                }
                            }
                        }
                    _uiState.update { it.copy(isConnecting = false, errorMessage = msg, status = null) }
                }
            }
        }
    }
}
