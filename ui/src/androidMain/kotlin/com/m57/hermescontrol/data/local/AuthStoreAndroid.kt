package com.m57.hermescontrol.data.local

import com.m57.hermescontrol.data.config.ConnectionProfile
import com.m57.hermescontrol.data.model.PinnedModel
import com.m57.hermescontrol.data.remote.ServerEndpoint
import com.m57.hermescontrol.data.theme.ThemePreference
import com.m57.hermescontrol.data.theme.ThemePreset
import kotlinx.coroutines.flow.StateFlow

/**
 * Android binding: every call delegates to [AuthManager], which owns the encrypted
 * store, the server store and the cookie jar.
 */
actual val authStore: AuthStore =
    object : AuthStore {
        override val chatFontScaleStops: List<Float> get() = AuthManager.chatFontScaleStops

        override val selectedProfileIdFlow: StateFlow<String> get() = AuthManager.selectedProfileIdFlow

        override fun getSelectedProfileId(): String? = AuthManager.getSelectedProfileId()

        override fun setSelectedProfileId(id: String?) = AuthManager.setSelectedProfileId(id)

        override fun getConnectionProfiles(): List<ConnectionProfile> = AuthManager.getConnectionProfiles()

        override fun saveConnectionProfiles(profiles: List<ConnectionProfile>) =
            AuthManager.saveConnectionProfiles(profiles)

        override fun saveConnectionProfilesAndToken(
            profiles: List<ConnectionProfile>,
            profileId: String,
            token: String?,
        ) = AuthManager.saveConnectionProfilesAndToken(profiles, profileId, token)

        override fun saveConnectionProfilesAndSelect(
            profiles: List<ConnectionProfile>,
            profileId: String,
            token: String?,
        ) = AuthManager.saveConnectionProfilesAndSelect(profiles, profileId, token)

        override fun ensureDefaultProfile() = AuthManager.ensureDefaultProfile()

        override fun ensureDefaultSelected() = AuthManager.ensureDefaultSelected()

        override fun getToken(): String? = AuthManager.getToken()

        override fun setToken(token: String?) = AuthManager.setToken(token)

        override fun getProfileToken(profileId: String): String? = AuthManager.getProfileToken(profileId)

        override fun setProfileToken(
            profileId: String,
            token: String?,
        ) = AuthManager.setProfileToken(profileId, token)

        override fun getBaseUrl(): String = AuthManager.getBaseUrl()

        override fun setBaseUrl(baseUrl: String) = AuthManager.setBaseUrl(baseUrl)

        override fun endpoint(): ServerEndpoint = AuthManager.endpoint()

        override fun getSessionCookie(): String? = AuthManager.getSessionCookie()

        override fun setSessionCookie(cookie: String?) = AuthManager.setSessionCookie(cookie)

        override fun getWsAuthParam(): String = AuthManager.getWsAuthParam()

        override fun setWsAuthParam(param: String) = AuthManager.setWsAuthParam(param)

        override fun isGatedMode(): Boolean = AuthManager.isGatedMode()

        override fun isAutoReconnect(): Boolean = AuthManager.isAutoReconnect()

        override fun setAutoReconnect(enabled: Boolean) = AuthManager.setAutoReconnect(enabled)

        override fun getPinnedModels(): List<PinnedModel> = AuthManager.getPinnedModels()

        override fun savePinnedModels(pinned: List<PinnedModel>) = AuthManager.savePinnedModels(pinned)

        override fun getPinnedSessionIds(profileId: String?): List<String> = AuthManager.getPinnedSessionIds(profileId)

        override fun savePinnedSessionIds(
            pinnedSessionIds: List<String>,
            profileId: String?,
        ) = AuthManager.savePinnedSessionIds(pinnedSessionIds, profileId)

        override fun clearPinnedSessionIds(profileId: String) = AuthManager.clearPinnedSessionIds(profileId)

        override fun getThemePreference(): ThemePreference = AuthManager.getThemePreference()

        override fun setThemePreference(theme: ThemePreference) = AuthManager.setThemePreference(theme)

        override fun getThemePreset(): ThemePreset = AuthManager.getThemePreset()

        override fun setThemePreset(preset: ThemePreset) = AuthManager.setThemePreset(preset)

        override fun isUseDynamicColors(): Boolean = AuthManager.isUseDynamicColors()

        override fun setUseDynamicColors(value: Boolean) = AuthManager.setUseDynamicColors(value)

        override fun getChatFontScale(): Float = AuthManager.getChatFontScale()

        override fun setChatFontScale(scale: Float) = AuthManager.setChatFontScale(scale)

        override fun normalizeChatFontScale(scale: Float): Float = AuthManager.normalizeChatFontScale(scale)

        override fun isTypingEffectEnabled(): Boolean = AuthManager.isTypingEffectEnabled()

        override fun setTypingEffectEnabled(enabled: Boolean) = AuthManager.setTypingEffectEnabled(enabled)

        override fun getTypingEffectDelayMs(): Int = AuthManager.getTypingEffectDelayMs()

        override fun setTypingEffectDelayMs(delayMs: Int) = AuthManager.setTypingEffectDelayMs(delayMs)

        override fun getAppLanguage(): String = AuthManager.getAppLanguage()

        override fun setAppLanguage(code: String) = AuthManager.setAppLanguage(code)
    }
