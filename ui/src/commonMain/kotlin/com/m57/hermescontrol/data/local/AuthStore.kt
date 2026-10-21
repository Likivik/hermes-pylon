package com.m57.hermescontrol.data.local

import com.m57.hermescontrol.data.config.ConnectionProfile
import com.m57.hermescontrol.data.model.PinnedModel
import com.m57.hermescontrol.data.remote.ServerEndpoint
import com.m57.hermescontrol.data.theme.ThemePreference
import com.m57.hermescontrol.data.theme.ThemePreset
import kotlinx.coroutines.flow.StateFlow

/**
 * The auth + preference surface the shared UI depends on.
 *
 * Android backs this with `AuthManager` (encrypted storage, server store, cookie
 * jar); desktop and web use an in-memory implementation. Keeping the port narrow
 * means screens and ViewModels never touch a platform storage API directly — that
 * is what lets them live in commonMain.
 */
object AuthDefaults {
    const val PROFILE_ID: String = "default"
    const val PROFILE_NAME: String = "Default"
}

interface AuthStore {
    /** Selectable chat font scales, ascending. */
    val chatFontScaleStops: List<Float>

    val selectedProfileIdFlow: StateFlow<String>

    fun getSelectedProfileId(): String?

    fun setSelectedProfileId(id: String?)

    // ── Connection profiles ─────────────────────────────────────────────
    fun getConnectionProfiles(): List<ConnectionProfile>

    fun saveConnectionProfiles(profiles: List<ConnectionProfile>)

    fun saveConnectionProfilesAndToken(
        profiles: List<ConnectionProfile>,
        profileId: String,
        token: String?,
    )

    fun saveConnectionProfilesAndSelect(
        profiles: List<ConnectionProfile>,
        profileId: String,
        token: String?,
    )

    fun ensureDefaultProfile()

    fun ensureDefaultSelected()

    // ── Tokens / endpoint ───────────────────────────────────────────────
    fun getToken(): String?

    fun setToken(token: String?)

    fun getProfileToken(profileId: String): String?

    fun setProfileToken(
        profileId: String,
        token: String?,
    )

    fun getBaseUrl(): String

    fun setBaseUrl(baseUrl: String)

    fun endpoint(): ServerEndpoint

    fun getSessionCookie(): String?

    fun setSessionCookie(cookie: String?)

    // ── WebSocket auth ──────────────────────────────────────────────────
    fun getWsAuthParam(): String

    fun setWsAuthParam(param: String)

    fun isGatedMode(): Boolean

    fun isAutoReconnect(): Boolean

    fun setAutoReconnect(enabled: Boolean)

    // ── Pinned items ────────────────────────────────────────────────────
    fun getPinnedModels(): List<PinnedModel>

    fun savePinnedModels(pinned: List<PinnedModel>)

    fun getPinnedSessionIds(profileId: String? = getSelectedProfileId()): List<String>

    fun savePinnedSessionIds(
        pinnedSessionIds: List<String>,
        profileId: String? = getSelectedProfileId(),
    )

    fun clearPinnedSessionIds(profileId: String)

    // ── Appearance ──────────────────────────────────────────────────────
    fun getThemePreference(): ThemePreference

    fun setThemePreference(theme: ThemePreference)

    fun getThemePreset(): ThemePreset

    fun setThemePreset(preset: ThemePreset)

    fun isUseDynamicColors(): Boolean

    fun setUseDynamicColors(value: Boolean)

    fun getChatFontScale(): Float

    fun setChatFontScale(scale: Float)

    fun normalizeChatFontScale(scale: Float): Float

    fun isTypingEffectEnabled(): Boolean

    fun setTypingEffectEnabled(enabled: Boolean)

    fun getTypingEffectDelayMs(): Int

    fun setTypingEffectDelayMs(delayMs: Int)

    // ── Language ────────────────────────────────────────────────────────
    fun getAppLanguage(): String

    fun setAppLanguage(code: String)
}

/** The active store for this target. */
expect val authStore: AuthStore
