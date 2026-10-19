package com.m57.hermescontrol.data.local

import com.m57.hermescontrol.data.config.ConnectionProfile
import com.m57.hermescontrol.data.model.PinnedModel
import com.m57.hermescontrol.data.remote.CleartextPolicy
import com.m57.hermescontrol.data.remote.ServerEndpoint
import com.m57.hermescontrol.data.theme.ThemePreference
import com.m57.hermescontrol.data.theme.ThemePreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory auth store for the desktop and web targets.
 *
 * Nothing is persisted: a browser or desktop session starts empty and the values
 * live for the life of the process. This is enough for the shared UI to render and
 * navigate; durable storage is an Android concern until a multiplatform backend
 * (DataStore / SQLDelight) is added.
 */
class InMemoryAuthStore : AuthStore {
    private val _selectedProfileIdFlow = MutableStateFlow(DEFAULT_PROFILE_ID)
    override val chatFontScaleStops: List<Float> = CHAT_FONT_SCALE_STOPS

    override val selectedProfileIdFlow: StateFlow<String> = _selectedProfileIdFlow.asStateFlow()

    private var profiles: List<ConnectionProfile> = emptyList()
    private val tokens = mutableMapOf<String, String>()
    private val pinnedSessions = mutableMapOf<String, List<String>>()
    private var pinnedModels: List<PinnedModel> = emptyList()

    private var baseUrl: String = DEFAULT_BASE_URL
    private var sessionCookie: String? = null
    private var wsAuthParam: String = "ticket"
    private var autoReconnect: Boolean = true

    private var themePreference = ThemePreference.SYSTEM
    private var themePreset = ThemePreset.DEFAULT
    private var dynamicColors = false
    private var chatFontScale = 1.0f
    private var typingEffectEnabled = true
    private var typingEffectDelayMs = 24
    private var appLanguage = "en"

    override fun getSelectedProfileId(): String? = _selectedProfileIdFlow.value

    override fun setSelectedProfileId(id: String?) {
        _selectedProfileIdFlow.value = id ?: DEFAULT_PROFILE_ID
    }

    override fun getConnectionProfiles(): List<ConnectionProfile> = profiles

    override fun saveConnectionProfiles(profiles: List<ConnectionProfile>) {
        this.profiles = profiles
    }

    override fun saveConnectionProfilesAndToken(
        profiles: List<ConnectionProfile>,
        profileId: String,
        token: String?,
    ) {
        this.profiles = profiles
        setProfileToken(profileId, token)
    }

    override fun saveConnectionProfilesAndSelect(
        profiles: List<ConnectionProfile>,
        profileId: String,
        token: String?,
    ) {
        saveConnectionProfilesAndToken(profiles, profileId, token)
        setSelectedProfileId(profileId)
    }

    override fun ensureDefaultProfile() {
        if (profiles.isEmpty()) profiles = emptyList()
    }

    override fun ensureDefaultSelected() {
        if (_selectedProfileIdFlow.value.isBlank()) _selectedProfileIdFlow.value = DEFAULT_PROFILE_ID
    }

    override fun getToken(): String? = tokens[_selectedProfileIdFlow.value]

    override fun setToken(token: String?) {
        val id = _selectedProfileIdFlow.value
        if (token.isNullOrBlank()) tokens.remove(id) else tokens[id] = token
    }

    override fun getProfileToken(profileId: String): String? = tokens[profileId]

    override fun setProfileToken(
        profileId: String,
        token: String?,
    ) {
        if (token.isNullOrBlank()) tokens.remove(profileId) else tokens[profileId] = token
    }

    override fun getBaseUrl(): String = baseUrl

    override fun setBaseUrl(baseUrl: String) {
        this.baseUrl = baseUrl
    }

    override fun endpoint(): ServerEndpoint = ServerEndpoint.parse(baseUrl, CleartextPolicy.ALLOW_WITH_WARNING)

    override fun getSessionCookie(): String? = sessionCookie

    override fun setSessionCookie(cookie: String?) {
        sessionCookie = cookie
    }

    override fun getWsAuthParam(): String = wsAuthParam

    override fun setWsAuthParam(param: String) {
        wsAuthParam = param
    }

    override fun isGatedMode(): Boolean = wsAuthParam == "ticket"

    override fun isAutoReconnect(): Boolean = autoReconnect

    override fun setAutoReconnect(enabled: Boolean) {
        autoReconnect = enabled
    }

    override fun getPinnedModels(): List<PinnedModel> = pinnedModels

    override fun savePinnedModels(pinned: List<PinnedModel>) {
        pinnedModels = pinned
    }

    override fun getPinnedSessionIds(profileId: String?): List<String> =
        pinnedSessions[profileId ?: _selectedProfileIdFlow.value].orEmpty()

    override fun savePinnedSessionIds(
        pinnedSessionIds: List<String>,
        profileId: String?,
    ) {
        pinnedSessions[profileId ?: _selectedProfileIdFlow.value] = pinnedSessionIds
    }

    override fun clearPinnedSessionIds(profileId: String) {
        pinnedSessions.remove(profileId)
    }

    override fun getThemePreference(): ThemePreference = themePreference

    override fun setThemePreference(theme: ThemePreference) {
        themePreference = theme
    }

    override fun getThemePreset(): ThemePreset = themePreset

    override fun setThemePreset(preset: ThemePreset) {
        themePreset = preset
    }

    override fun isUseDynamicColors(): Boolean = dynamicColors

    override fun setUseDynamicColors(value: Boolean) {
        dynamicColors = value
    }

    override fun getChatFontScale(): Float = chatFontScale

    override fun setChatFontScale(scale: Float) {
        chatFontScale = normalizeChatFontScale(scale)
    }

    override fun normalizeChatFontScale(scale: Float): Float =
        CHAT_FONT_SCALE_STOPS.minBy { kotlin.math.abs(it - scale) }

    override fun isTypingEffectEnabled(): Boolean = typingEffectEnabled

    override fun setTypingEffectEnabled(enabled: Boolean) {
        typingEffectEnabled = enabled
    }

    override fun getTypingEffectDelayMs(): Int = typingEffectDelayMs

    override fun setTypingEffectDelayMs(delayMs: Int) {
        typingEffectDelayMs = delayMs
    }

    override fun getAppLanguage(): String = appLanguage

    override fun setAppLanguage(code: String) {
        appLanguage = code
    }

    private companion object {
        const val DEFAULT_PROFILE_ID = "default"
        const val DEFAULT_BASE_URL = "http://127.0.0.1:8787/"
        val CHAT_FONT_SCALE_STOPS = listOf(0.85f, 1.0f, 1.15f, 1.3f, 1.5f)
    }
}
