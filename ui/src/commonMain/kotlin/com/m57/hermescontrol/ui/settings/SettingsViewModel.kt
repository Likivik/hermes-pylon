// Modified from Hy4ri/hermes-mobile for this fork; see NOTICE.

package com.m57.hermescontrol.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m57.hermescontrol.AppEnvironment
import com.m57.hermescontrol.data.config.ConnectionProfile
import com.m57.hermescontrol.data.config.resolveBaseUrl
import com.m57.hermescontrol.data.local.AuthDefaults
import com.m57.hermescontrol.data.local.authStore
import com.m57.hermescontrol.data.remote.ApiClient
import com.m57.hermescontrol.data.remote.CleartextPolicy
import com.m57.hermescontrol.data.remote.NetworkResult
import com.m57.hermescontrol.data.remote.ServerEndpoint
import com.m57.hermescontrol.data.remote.parseForBuild
import com.m57.hermescontrol.data.remote.safeApiCall
import com.m57.hermescontrol.data.theme.ThemePreference
import com.m57.hermescontrol.data.theme.ThemePreset
import com.m57.hermescontrol.data.ws.HermesWsClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.m57.hermescontrol.util.ioDispatcher as ioDispatcher

data class SettingsUiState(
    val baseUrl: String = ServerEndpoint.DEFAULT_BASE_URL,
    val transportWarning: String? = null,
    val host: String = "127.0.0.1",
    val port: String = "9119",
    val token: String = "",
    val autoReconnect: Boolean = true,
    val themePreference: ThemePreference = ThemePreference.SYSTEM,
    val useDynamicColors: Boolean = false,
    val themePreset: ThemePreset = ThemePreset.DEFAULT,
    val chatFontScale: Float = 1.0f,
    val isTesting: Boolean = false,
    val testResult: String? = null,
    val isSaved: Boolean = false,
    val typingEffectEnabled: Boolean = false,
    val typingEffectDelayMs: Int = 30,
    val profiles: List<ConnectionProfile> = emptyList(),
    val selectedProfileId: String? = null,
    val renameProfileName: String = "",
    val appLanguage: String = "system",
    // Profile add/edit dialog
    val showProfileDialog: Boolean = false,
    val editingProfileId: String? = null,
    val dialogProfileName: String = "",
    val dialogProfileBaseUrl: String = ServerEndpoint.DEFAULT_BASE_URL,
    val dialogProfileTransportWarning: String? = null,
    val dialogProfileError: String? = null,
    val dialogProfileToken: String = "",
    // Delete confirmation
    val showDeleteConfirm: Boolean = false,
    val profileToDeleteId: String? = null,
    val profileToDeleteName: String = "",
    val navigateToLogin: Boolean = false,
)

class SettingsViewModel(
    private val ioDispatcher: CoroutineDispatcher = AppEnvironment.current.io,
    private val disconnectWebSocket: () -> Unit = {
        HermesWsClient.disconnect(clearPendingMessages = true)
    },
    private val connectWebSocket: () -> Unit = {
        HermesWsClient.connect()
    },
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(ioDispatcher) {
            loadSettings()
        }
    }

    private suspend fun loadSettings() {
        val selectedId = authStore.getSelectedProfileId()
        val endpoint = authStore.endpoint()
        val host = endpoint.baseUrl.host
        val port = endpoint.baseUrl.port.toString()
        val token = authStore.getToken() ?: ""
        val autoReconnect = authStore.isAutoReconnect()
        val themePreference = authStore.getThemePreference()
        val useDynamicColors = authStore.isUseDynamicColors()
        val themePreset = authStore.getThemePreset()
        val chatFontScale = authStore.getChatFontScale()
        val typingEffectEnabled = authStore.isTypingEffectEnabled()
        val typingEffectDelayMs = authStore.getTypingEffectDelayMs()
        val profiles = authStore.getConnectionProfiles()
        val appLanguage = authStore.getAppLanguage()
        val renameProfileName =
            profiles.firstOrNull { p -> p.id == selectedId }?.name ?: ""
        _uiState.update {
            it.copy(
                baseUrl = endpoint.baseUrl.toString(),
                transportWarning = endpoint.securityWarning,
                host = host,
                port = port,
                token = token,
                autoReconnect = autoReconnect,
                themePreference = themePreference,
                useDynamicColors = useDynamicColors,
                themePreset = themePreset,
                chatFontScale = chatFontScale,
                typingEffectEnabled = typingEffectEnabled,
                typingEffectDelayMs = typingEffectDelayMs,
                profiles = profiles,
                selectedProfileId = selectedId,
                renameProfileName = renameProfileName,
                appLanguage = appLanguage,
            )
        }
    }

    fun selectProfile(profileId: String?) {
        val profileChanged = profileId != authStore.getSelectedProfileId()
        if (profileChanged) {
            disconnectWebSocket()
        }
        authStore.setSelectedProfileId(profileId)
        viewModelScope.launch(ioDispatcher) { loadSettings() }
        ApiClient.rebuild()
        if (profileChanged) {
            connectWebSocket()
        }
    }

    fun deleteProfile(profileId: String) {
        val wasSelected = authStore.getSelectedProfileId() == profileId
        if (wasSelected) {
            disconnectWebSocket()
        }
        val updatedProfiles = authStore.getConnectionProfiles().filter { it.id != profileId }
        authStore.saveConnectionProfiles(updatedProfiles)
        authStore.setProfileToken(profileId, null)
        authStore.clearPinnedSessionIds(profileId)
        // Never leave selection null (issue #478): if the deleted profile was selected,
        // fall back to the default profile instead of clearing selection.
        if (wasSelected) {
            if (updatedProfiles.none { it.id == AuthDefaults.PROFILE_ID }) {
                authStore.ensureDefaultProfile()
            }
            authStore.setSelectedProfileId(AuthDefaults.PROFILE_ID)
        }
        viewModelScope.launch(ioDispatcher) { loadSettings() }
        ApiClient.rebuild()
        if (wasSelected) {
            connectWebSocket()
        }
    }

    // ── Profile add/edit dialog ──────────────────────────────────────────

    fun openAddProfile() {
        _uiState.update {
            it.copy(
                showProfileDialog = true,
                editingProfileId = null,
                dialogProfileName = "",
                dialogProfileBaseUrl = ServerEndpoint.DEFAULT_BASE_URL,
                dialogProfileTransportWarning = null,
                dialogProfileError = null,
                dialogProfileToken = "",
            )
        }
    }

    fun openEditProfile(profileId: String) {
        val profile = authStore.getConnectionProfiles().firstOrNull { it.id == profileId } ?: return
        val token = authStore.getProfileToken(profileId) ?: ""
        _uiState.update {
            it.copy(
                showProfileDialog = true,
                editingProfileId = profileId,
                dialogProfileName = profile.name,
                dialogProfileBaseUrl = profile.resolveBaseUrl(authStore.getBaseUrl()),
                dialogProfileTransportWarning =
                    ServerEndpoint.parse(
                        profile.resolveBaseUrl(authStore.getBaseUrl()),
                        CleartextPolicy.ALLOW_WITH_WARNING,
                    ).securityWarning,
                dialogProfileError = null,
                dialogProfileToken = token,
            )
        }
    }

    fun closeProfileDialog() {
        _uiState.update {
            it.copy(showProfileDialog = false, editingProfileId = null)
        }
    }

    fun onDialogProfileNameChange(value: String) {
        _uiState.update { it.copy(dialogProfileName = value) }
    }

    fun onDialogProfileBaseUrlChange(value: String) {
        val normalized = value.trim()
        val warning =
            runCatching {
                ServerEndpoint.parseForBuild(normalized).securityWarning
            }.getOrNull()
        _uiState.update {
            it.copy(
                dialogProfileBaseUrl = normalized,
                dialogProfileTransportWarning = warning,
                dialogProfileError = null,
            )
        }
    }

    fun onDialogProfileTokenChange(value: String) {
        _uiState.update { it.copy(dialogProfileToken = value.trim()) }
    }

    fun saveProfileFromDialog() {
        val state = _uiState.value
        val name = state.dialogProfileName.trim()
        val endpoint =
            try {
                ServerEndpoint.parseForBuild(state.dialogProfileBaseUrl)
            } catch (e: IllegalArgumentException) {
                _uiState.update { it.copy(dialogProfileError = e.message) }
                return
            }
        val token = state.dialogProfileToken

        if (name.isBlank()) return

        val profiles = authStore.getConnectionProfiles().toMutableList()
        val editingId = state.editingProfileId
        var reconnectWebSocket = false

        if (editingId != null) {
            // Update existing profile
            val index = profiles.indexOfFirst { it.id == editingId }
            if (index == -1) return
            val oldToken = authStore.getProfileToken(editingId)
            val oldBaseUrl = profiles[index].resolveBaseUrl(authStore.getBaseUrl())
            val newBaseUrl = endpoint.baseUrl.toString()
            // Null and empty both mean "no token"; every other token change
            // requires rebuilding the active connection before credentials
            // are mutated.
            val tokenChanged = token.orEmpty() != oldToken.orEmpty()
            // Tear down the active socket only when connection-relevant
            // fields change; a cosmetic rename must preserve queued prompts.
            val connectionChanged = tokenChanged || newBaseUrl != oldBaseUrl
            if (editingId == authStore.getSelectedProfileId() && connectionChanged) {
                disconnectWebSocket()
                reconnectWebSocket = true
            }
            profiles[index] =
                profiles[index].copy(
                    name = name,
                    host = "",
                    port = 0,
                    baseUrl = endpoint.baseUrl.toString(),
                )
            if (tokenChanged) {
                authStore.saveConnectionProfilesAndToken(profiles, editingId, token)
            } else {
                authStore.saveConnectionProfiles(profiles)
            }
        } else {
            // Add new profile
            val newProfile =
                ConnectionProfile(
                    name = name,
                    baseUrl = endpoint.baseUrl.toString(),
                )
            profiles.add(newProfile)
            authStore.saveConnectionProfiles(profiles)
            authStore.setProfileToken(newProfile.id, "")
            disconnectWebSocket()
            authStore.setSelectedProfileId(newProfile.id)
            _uiState.update { it.copy(navigateToLogin = true) }
        }

        closeProfileDialog()
        viewModelScope.launch(ioDispatcher) { loadSettings() }
        ApiClient.rebuild()
        if (reconnectWebSocket) {
            connectWebSocket()
        }
    }

    // ── Delete confirmation ──────────────────────────────────────────────

    fun requestDeleteProfile(profileId: String) {
        val profile = authStore.getConnectionProfiles().firstOrNull { it.id == profileId }
        _uiState.update {
            it.copy(
                showDeleteConfirm = true,
                profileToDeleteId = profileId,
                profileToDeleteName = profile?.name ?: "",
            )
        }
    }

    fun confirmDeleteProfile() {
        val profileId = _uiState.value.profileToDeleteId ?: return
        deleteProfile(profileId)
        _uiState.update {
            it.copy(showDeleteConfirm = false, profileToDeleteId = null, profileToDeleteName = "")
        }
    }

    fun cancelDeleteProfile() {
        _uiState.update {
            it.copy(showDeleteConfirm = false, profileToDeleteId = null, profileToDeleteName = "")
        }
    }

    fun onBaseUrlChange(value: String) {
        val normalized = value.trim()
        val endpoint =
            runCatching {
                ServerEndpoint.parse(
                    normalized,
                    CleartextPolicy.ALLOW_WITH_WARNING,
                )
            }.getOrNull()
        _uiState.update {
            it.copy(
                baseUrl = normalized,
                transportWarning = endpoint?.securityWarning,
                host = endpoint?.baseUrl?.host ?: it.host,
                port = endpoint?.baseUrl?.port?.toString() ?: it.port,
                isSaved = false,
            )
        }
    }

    fun onHostChange(value: String) {
        val current =
            ServerEndpoint.parse(
                _uiState.value.baseUrl,
                CleartextPolicy.ALLOW_WITH_WARNING,
            )
        val host = value.trim().removePrefix("[").removeSuffix("]")
        onBaseUrlChange(current.baseUrl.newBuilder().host(host).build().toString())
    }

    fun onPortChange(value: String) {
        val port = value.filter { c -> c.isDigit() }.toIntOrNull()
        if (port == null || port !in 1..65535) {
            _uiState.update { it.copy(port = value, isSaved = false) }
            return
        }
        val current =
            ServerEndpoint.parse(
                _uiState.value.baseUrl,
                CleartextPolicy.ALLOW_WITH_WARNING,
            )
        onBaseUrlChange(current.baseUrl.newBuilder().port(port).build().toString())
    }

    fun onTokenChange(value: String) {
        _uiState.update { it.copy(token = value.trim(), isSaved = false) }
    }

    fun onAutoReconnectChange(enabled: Boolean) {
        _uiState.update { it.copy(autoReconnect = enabled, isSaved = false) }
        authStore.setAutoReconnect(enabled)
    }

    fun onThemeChange(theme: ThemePreference) {
        _uiState.update { it.copy(themePreference = theme, isSaved = false) }
        authStore.setThemePreference(theme)
    }

    fun onChatFontScaleChange(scale: Float) {
        val normalized = authStore.normalizeChatFontScale(scale)
        _uiState.update { it.copy(chatFontScale = normalized, isSaved = false) }
        authStore.setChatFontScale(normalized)
    }

    fun onUseDynamicColorsChange(enabled: Boolean) {
        _uiState.update { it.copy(useDynamicColors = enabled, isSaved = false) }
        authStore.setUseDynamicColors(enabled)
    }

    fun onThemePresetChange(preset: ThemePreset) {
        _uiState.update { it.copy(themePreset = preset, isSaved = false) }
        authStore.setThemePreset(preset)
    }

    fun onAppLanguageChange(code: String) {
        _uiState.update { it.copy(appLanguage = code, isSaved = false) }
        authStore.setAppLanguage(code)
    }

    fun onTypingEffectEnabledChange(enabled: Boolean) {
        _uiState.update { it.copy(typingEffectEnabled = enabled, isSaved = false) }
        authStore.setTypingEffectEnabled(enabled)
    }

    fun onTypingEffectDelayMsChange(delayMs: Int) {
        _uiState.update { it.copy(typingEffectDelayMs = delayMs, isSaved = false) }
        authStore.setTypingEffectDelayMs(delayMs)
    }

    /** Clear all auth credentials — logs out and returns to landing screen. */
    fun logout() {
        // Tear down the authenticated socket before discarding credentials;
        // a live socket must never outlive the identity it was opened with.
        disconnectWebSocket()
        authStore.setToken(null)
        authStore.setSessionCookie(null)
        authStore.setWsAuthParam("token")
        // Don't rebuild ApiClient here — let the navigation complete first
    }

    fun save() {
        val state = _uiState.value
        val endpoint =
            try {
                ServerEndpoint.parseForBuild(state.baseUrl)
            } catch (e: IllegalArgumentException) {
                _uiState.update { it.copy(testResult = e.message) }
                return
            }

        authStore.setBaseUrl(endpoint.baseUrl.toString())
        authStore.setToken(state.token)
        authStore.setAutoReconnect(state.autoReconnect)
        // B6 (Jun 18 2026, kanban t_86e9be9b): persist theme choice so it
        // survives a cold start (was previously dropped on save()).
        authStore.setThemePreference(state.themePreference)
        authStore.setUseDynamicColors(state.useDynamicColors)
        authStore.setThemePreset(state.themePreset)
        authStore.setTypingEffectEnabled(state.typingEffectEnabled)
        authStore.setTypingEffectDelayMs(state.typingEffectDelayMs)
        ApiClient.rebuild()

        viewModelScope.launch(ioDispatcher) {
            loadSettings()
            _uiState.update { it.copy(isSaved = true, testResult = null) }
        }
    }

    fun testConnection() {
        val state = _uiState.value
        if (state.token.isBlank()) {
            _uiState.update { it.copy(testResult = "❌ Token is required") }
            return
        }

        // Save first so ApiClient uses updated settings
        save()

        _uiState.update { it.copy(isTesting = true, testResult = null) }

        viewModelScope.launch {
            val result =
                withContext(ioDispatcher) {
                    safeApiCall { ApiClient.hermesApi.getStatus() }
                }
            when (result) {
                is NetworkResult.Success -> {
                    _uiState.update {
                        it.copy(isTesting = false, testResult = "✅ Connected successfully")
                    }
                }

                is NetworkResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isTesting = false,
                            testResult = "❌ ${result.error.message}",
                        )
                    }
                }
            }
        }
    }
}
