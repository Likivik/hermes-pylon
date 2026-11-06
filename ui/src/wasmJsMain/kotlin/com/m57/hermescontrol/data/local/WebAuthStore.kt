package com.m57.hermescontrol.data.local

import com.m57.hermescontrol.data.config.ConnectionProfile
import com.m57.hermescontrol.data.model.PinnedModel
import com.m57.hermescontrol.data.theme.ThemePreference
import com.m57.hermescontrol.data.theme.ThemePreset
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Browser auth store: an [InMemoryAuthStore] that survives a reload.
 *
 * Before this, the web target used the in-memory store, so every reload threw
 * away the server URL, the profiles, the theme and the session — the user
 * retyped everything. Now:
 *
 * - **Non-secrets go to `localStorage`**: server URL, profiles, selected profile,
 *   theme, language, font scale, typing effect, auto-reconnect, pinned models and
 *   pinned sessions. None of it is a credential.
 * - **Secrets go to `sessionStorage`**: per-profile tokens and the session cookie.
 *   Per tab: they survive a reload and die with the tab. Deliberately not
 *   `localStorage` — a token at rest in a browser is readable by any script, so it
 *   does not get to persist across restarts (issue #128).
 *
 * The same-origin deployment needs none of this for authentication: the gateway's
 * HttpOnly cookie is what keeps the session, and `GET /api/status` is the probe.
 * The token here exists for a pasted session token and for the WebSocket ticket.
 *
 * Storage failures (private mode, quota, disabled storage) degrade to in-memory
 * behaviour rather than throwing — same contract as `PreferencesStoreWasm`.
 */
class WebAuthStore(
    private val delegate: InMemoryAuthStore = InMemoryAuthStore(),
) : AuthStore by delegate {
    init {
        restore()
    }

    // ── Persisted, non-secret ────────────────────────────────────────────────

    override fun setSelectedProfileId(id: String?) {
        delegate.setSelectedProfileId(id)
        putPrefs()
    }

    override fun saveConnectionProfiles(profiles: List<ConnectionProfile>) {
        delegate.saveConnectionProfiles(profiles)
        putPrefs()
    }

    override fun saveConnectionProfilesAndToken(
        profiles: List<ConnectionProfile>,
        profileId: String,
        token: String?,
    ) {
        delegate.saveConnectionProfilesAndToken(profiles, profileId, token)
        putPrefs()
        putSecrets()
    }

    override fun saveConnectionProfilesAndSelect(
        profiles: List<ConnectionProfile>,
        profileId: String,
        token: String?,
    ) {
        delegate.saveConnectionProfilesAndSelect(profiles, profileId, token)
        putPrefs()
        putSecrets()
    }

    override fun setBaseUrl(baseUrl: String) {
        delegate.setBaseUrl(baseUrl)
        putPrefs()
    }

    override fun setWsAuthParam(param: String) {
        delegate.setWsAuthParam(param)
        putPrefs()
    }

    override fun setAutoReconnect(enabled: Boolean) {
        delegate.setAutoReconnect(enabled)
        putPrefs()
    }

    override fun savePinnedModels(pinned: List<PinnedModel>) {
        delegate.savePinnedModels(pinned)
        putPrefs()
    }

    override fun savePinnedSessionIds(
        pinnedSessionIds: List<String>,
        profileId: String?,
    ) {
        delegate.savePinnedSessionIds(pinnedSessionIds, profileId)
        putPrefs()
    }

    override fun clearPinnedSessionIds(profileId: String) {
        delegate.clearPinnedSessionIds(profileId)
        putPrefs()
    }

    override fun setThemePreference(theme: ThemePreference) {
        delegate.setThemePreference(theme)
        putPrefs()
    }

    override fun setThemePreset(preset: ThemePreset) {
        delegate.setThemePreset(preset)
        putPrefs()
    }

    override fun setUseDynamicColors(value: Boolean) {
        delegate.setUseDynamicColors(value)
        putPrefs()
    }

    override fun setChatFontScale(scale: Float) {
        delegate.setChatFontScale(scale)
        putPrefs()
    }

    override fun setTypingEffectEnabled(enabled: Boolean) {
        delegate.setTypingEffectEnabled(enabled)
        putPrefs()
    }

    override fun setTypingEffectDelayMs(delayMs: Int) {
        delegate.setTypingEffectDelayMs(delayMs)
        putPrefs()
    }

    override fun setAppLanguage(code: String) {
        delegate.setAppLanguage(code)
        putPrefs()
    }

    // ── Persisted, secret (sessionStorage) ───────────────────────────────────

    override fun setToken(token: String?) {
        delegate.setToken(token)
        putSecrets()
    }

    override fun setProfileToken(
        profileId: String,
        token: String?,
    ) {
        delegate.setProfileToken(profileId, token)
        putSecrets()
    }

    override fun setSessionCookie(cookie: String?) {
        delegate.setSessionCookie(cookie)
        putSecrets()
    }

    // ── Restore ──────────────────────────────────────────────────────────────

    private fun restore() {
        runCatching {
            val prefs = jsPrefsGet(PREFS_KEY)?.let { Json.parseToJsonElement(it).jsonObject }
            if (prefs != null) {
                prefs["selectedProfileId"]?.jsonPrimitive?.contentOrNullOrNull()?.let {
                    delegate.setSelectedProfileId(it)
                }
                prefs["baseUrl"]?.jsonPrimitive?.contentOrNullOrNull()?.let { delegate.setBaseUrl(it) }
                prefs["wsAuthParam"]?.jsonPrimitive?.contentOrNullOrNull()?.let {
                    delegate.setWsAuthParam(it)
                }
                prefs["autoReconnect"]?.jsonPrimitive?.contentOrNullOrNull()?.toBooleanStrictOrNull()?.let {
                    delegate.setAutoReconnect(it)
                }
                prefs["profiles"]?.jsonArray?.let { arr ->
                    val profiles =
                        arr.mapNotNull { el ->
                            val o = el.jsonObject
                            val name = o["name"]?.jsonPrimitive?.contentOrNullOrNull() ?: return@mapNotNull null
                            ConnectionProfile(
                                id = o["id"]?.jsonPrimitive?.contentOrNullOrNull() ?: name,
                                name = name,
                                host = o["host"]?.jsonPrimitive?.contentOrNullOrNull() ?: "127.0.0.1",
                                port = o["port"]?.jsonPrimitive?.contentOrNullOrNull()?.toIntOrNull() ?: 9119,
                                baseUrl = o["baseUrl"]?.jsonPrimitive?.contentOrNullOrNull(),
                                wsAuthParam = o["wsAuthParam"]?.jsonPrimitive?.contentOrNullOrNull(),
                            )
                        }
                    if (profiles.isNotEmpty()) delegate.saveConnectionProfiles(profiles)
                }
                prefs["pinnedModels"]?.jsonArray?.let { arr ->
                    delegate.savePinnedModels(
                        arr.mapNotNull { el ->
                            val o = el.jsonObject
                            val provider = o["providerSlug"]?.jsonPrimitive?.contentOrNullOrNull()
                            val model = o["modelName"]?.jsonPrimitive?.contentOrNullOrNull()
                            if (provider == null || model == null) null else PinnedModel(provider, model)
                        },
                    )
                }
                prefs["pinnedSessions"]?.jsonObject?.forEach { (profileId, ids) ->
                    delegate.savePinnedSessionIds(
                        ids.jsonArray.mapNotNull { it.jsonPrimitive.contentOrNullOrNull() },
                        profileId,
                    )
                }
                prefs["theme"]?.jsonPrimitive?.contentOrNullOrNull()?.let { name ->
                    ThemePreference.entries.firstOrNull { it.name == name }?.let(delegate::setThemePreference)
                }
                prefs["themePreset"]?.jsonPrimitive?.contentOrNullOrNull()?.let { name ->
                    ThemePreset.entries.firstOrNull { it.name == name }?.let(delegate::setThemePreset)
                }
                prefs["dynamicColors"]?.jsonPrimitive?.contentOrNullOrNull()?.toBooleanStrictOrNull()?.let {
                    delegate.setUseDynamicColors(it)
                }
                prefs["chatFontScale"]?.jsonPrimitive?.contentOrNullOrNull()?.toFloatOrNull()?.let {
                    delegate.setChatFontScale(it)
                }
                prefs["typingEffect"]?.jsonPrimitive?.contentOrNullOrNull()?.toBooleanStrictOrNull()?.let {
                    delegate.setTypingEffectEnabled(it)
                }
                prefs["typingDelayMs"]?.jsonPrimitive?.contentOrNullOrNull()?.toIntOrNull()?.let {
                    delegate.setTypingEffectDelayMs(it)
                }
                prefs["language"]?.jsonPrimitive?.contentOrNullOrNull()?.let { delegate.setAppLanguage(it) }
            }

            jsSessionGet(SECRETS_KEY)?.let { raw ->
                val secrets = Json.parseToJsonElement(raw).jsonObject
                secrets["cookie"]?.jsonPrimitive?.contentOrNullOrNull()?.let { delegate.setSessionCookie(it) }
                secrets["tokens"]?.jsonObject?.forEach { (profileId, token) ->
                    token.jsonPrimitive.contentOrNullOrNull()?.let { delegate.setProfileToken(profileId, it) }
                }
            }
        }
    }

    // ── Write-through ────────────────────────────────────────────────────────

    private fun putPrefs() {
        runCatching {
            val json =
                buildJsonObject {
                    put("selectedProfileId", delegate.getSelectedProfileId())
                    put("baseUrl", delegate.getBaseUrl())
                    put("wsAuthParam", delegate.getWsAuthParam())
                    put("autoReconnect", delegate.isAutoReconnect())
                    put(
                        "profiles",
                        buildJsonArray {
                            delegate.getConnectionProfiles().forEach { p ->
                                add(
                                    buildJsonObject {
                                        put("id", p.id)
                                        put("name", p.name)
                                        put("host", p.host)
                                        put("port", p.port)
                                        p.baseUrl?.let { put("baseUrl", it) }
                                        p.wsAuthParam?.let { put("wsAuthParam", it) }
                                    },
                                )
                            }
                        },
                    )
                    put(
                        "pinnedModels",
                        buildJsonArray {
                            delegate.getPinnedModels().forEach { m ->
                                add(
                                    buildJsonObject {
                                        put("providerSlug", m.providerSlug)
                                        put("modelName", m.modelName)
                                    },
                                )
                            }
                        },
                    )
                    put(
                        "pinnedSessions",
                        buildJsonObject {
                            delegate.getConnectionProfiles().forEach { p ->
                                put(
                                    p.id,
                                    buildJsonArray {
                                        delegate.getPinnedSessionIds(p.id).forEach { add(JsonPrimitive(it)) }
                                    },
                                )
                            }
                        },
                    )
                    put("theme", delegate.getThemePreference().name)
                    put("themePreset", delegate.getThemePreset().name)
                    put("dynamicColors", delegate.isUseDynamicColors())
                    put("chatFontScale", delegate.getChatFontScale().toString())
                    put("typingEffect", delegate.isTypingEffectEnabled())
                    put("typingDelayMs", delegate.getTypingEffectDelayMs())
                    put("language", delegate.getAppLanguage())
                }
            jsPrefsSet(PREFS_KEY, json.toString())
        }
    }

    private fun putSecrets() {
        runCatching {
            val tokens =
                buildJsonObject {
                    delegate.getConnectionProfiles().forEach { p ->
                        delegate.getProfileToken(p.id)?.let { put(p.id, it) }
                    }
                    // The selected profile may not be in the list yet (first login).
                    delegate.getSelectedProfileId()?.let { id ->
                        delegate.getProfileToken(id)?.let { put(id, it) }
                    }
                }
            val json =
                buildJsonObject {
                    put("tokens", tokens)
                    delegate.getSessionCookie()?.let { put("cookie", it) }
                }
            jsSessionSet(SECRETS_KEY, json.toString())
        }
    }

    private companion object {
        const val PREFS_KEY = "pylon.auth.state"
        const val SECRETS_KEY = "pylon.auth.secrets"
    }
}

/** `null` for JSON `null` and for missing values. */
private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullOrNull(): String? =
    if (this is kotlinx.serialization.json.JsonNull) null else content

@JsFun(
    """(k, v) => {
        try { window.localStorage.setItem(k, v); return true } catch (e) { return false }
    }""",
)
private external fun jsPrefsSet(
    key: String,
    value: String,
): Boolean

@JsFun("(k) => { try { return window.localStorage.getItem(k) } catch (e) { return null } }")
private external fun jsPrefsGet(key: String): String?

@JsFun(
    """(k, v) => {
        try { window.sessionStorage.setItem(k, v); return true } catch (e) { return false }
    }""",
)
private external fun jsSessionSet(
    key: String,
    value: String,
): Boolean

@JsFun("(k) => { try { return window.sessionStorage.getItem(k) } catch (e) { return null } }")
private external fun jsSessionGet(key: String): String?

@Suppress("unused")
private fun JsonObject.debugKeys(): Set<String> = keys
