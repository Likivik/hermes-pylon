// Modified from Hy4ri/hermes-mobile for this fork; see NOTICE.

package com.m57.hermescontrol.data.local

import com.m57.hermescontrol.data.config.ConnectionProfile
import com.m57.hermescontrol.data.config.ServerStore
import com.m57.hermescontrol.data.config.ServerStoreState
import com.m57.hermescontrol.data.config.resolveBaseUrl
import com.m57.hermescontrol.data.config.resolvedBaseUrl
import com.m57.hermescontrol.data.config.resolvedHost
import com.m57.hermescontrol.data.config.resolvedPort
import com.m57.hermescontrol.data.model.PinnedModel
import com.m57.hermescontrol.data.remote.CleartextPolicy
import com.m57.hermescontrol.data.remote.CookiePlatform
import com.m57.hermescontrol.data.remote.ServerEndpoint
import com.m57.hermescontrol.data.remote.parseForBuild
import com.m57.hermescontrol.data.security.SecretStore
import com.m57.hermescontrol.data.security.SecureBlobException
import com.m57.hermescontrol.data.security.authSecretKey
import com.m57.hermescontrol.data.security.holdsMonitor
import com.m57.hermescontrol.data.security.secureRandomBytes
import com.m57.hermescontrol.data.theme.ThemePreference
import com.m57.hermescontrol.data.theme.ThemePreset
import com.m57.hermescontrol.data.ws.WsAuthConfig
import com.m57.hermescontrol.util.ioDispatcher
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.io.encoding.Base64

/**
 * Singleton that manages encrypted storage of the Hermes dashboard token
 * and connection settings.
 *
 * Must call [init] with a Context before any other method.
 */
object AuthManager : WsAuthConfig {
    val chatFontScaleStops = listOf(0.85f, 1.0f, 1.15f, 1.3f, 1.5f)
    const val DEFAULT_PROFILE_ID = "default"
    const val DEFAULT_PROFILE_NAME = "Default"
    private const val KEY_SELECTED_PROFILE_ID = "selected_profile_id"
    private const val KEY_SESSION_COOKIE = "session_cookie"
    private const val KEY_LEGACY_TOKEN = "auth_token"
    private const val KEY_LEGACY_DEFAULT_MIGRATED = "legacy_default_migrated"
    private const val DATABASE_PASSWORD_BYTES = 32
    private val monitor = SynchronizedObject()

    private val databasePasswordLock = SynchronizedObject()

    @Volatile
    private var secureStorage: SecretStore? = null
    private var cookiePlatform: CookiePlatform? = null

    /**
     * Test/DI substitution point for the encrypted secret store.
     *
     * Nullable rather than defaulted because the production default needs an
     * Android `Context`; each platform supplies its own when this is null.
     */
    @Volatile
    var secretStoreFactory: (() -> SecretStore)? = null

    @Volatile
    private var _serverStore: ServerStore? = null

    @Volatile
    private var appScope: CoroutineScope? = null

    val serverStore: ServerStore
        get() =
            _serverStore ?: throw IllegalStateException(
                "AuthManager not initialized. Call init(context) first.",
            )

    private val _themePreferenceFlow = MutableStateFlow<ThemePreference>(ThemePreference.SYSTEM)
    val themePreferenceFlow: StateFlow<ThemePreference> = _themePreferenceFlow.asStateFlow()

    private val _useDynamicColorsFlow = MutableStateFlow<Boolean>(false)
    val useDynamicColorsFlow: StateFlow<Boolean> = _useDynamicColorsFlow.asStateFlow()

    private val _themePresetFlow = MutableStateFlow<ThemePreset>(ThemePreset.DEFAULT)
    val themePresetFlow: StateFlow<ThemePreset> = _themePresetFlow.asStateFlow()

    private val _chatFontScaleFlow = MutableStateFlow(1.0f)
    val chatFontScaleFlow: StateFlow<Float> = _chatFontScaleFlow.asStateFlow()

    private val _tokenFlow = MutableStateFlow<String?>(null)
    val tokenFlow: StateFlow<String?> = _tokenFlow.asStateFlow()

    @Volatile
    var beforeTokenFlowPublicationForTest: (() -> Unit)? = null

    private val _selectedProfileIdFlow = MutableStateFlow(DEFAULT_PROFILE_ID)
    val selectedProfileIdFlow: StateFlow<String> = _selectedProfileIdFlow.asStateFlow()

    /**
     * Initialise from a platform bundle. `AuthPlatform` supplies the server
     * store, the secret store and the cookie store, so the manager itself never
     * needs to know what a `Context` is. Android callers reach this through the
     * `init(context)` extension in `androidMain`.
     */
    fun init(platform: AuthPlatform) {
        if (_serverStore != null) return
        synchronized(monitor) {
            if (_serverStore != null) return

            val scope = CoroutineScope(ioDispatcher)
            appScope = scope
            val store = platform.createServerStore(scope)
            _serverStore = store

            cookiePlatform = platform.cookies
            val storage = platform.createSecretStore()
            secureStorage = storage
            migrateLegacyDefaultIfNeeded(storage)
            publishTokenLocked(getTokenInternal(storage))

            // Initialize the encrypted cookie store. Its initial scope loads on IO.
            val initialProfileId =
                store.getLatestState().selectedProfileId?.takeIf { it.isNotBlank() } ?: DEFAULT_PROFILE_ID
            _selectedProfileIdFlow.value = initialProfileId
            platform.initializeCookieStore(initialProfileId)

            scope.launch {
                store.stateFlow.collect { state ->
                    synchronized(monitor) {
                        val latestProfileId = store.getLatestState().selectedProfileId
                        _selectedProfileIdFlow.value = normalizedProfileId(latestProfileId)
                        syncCookieStoreForProfile(latestProfileId)
                    }
                    _themePreferenceFlow.value = state.themePreference
                    _useDynamicColorsFlow.value = state.useDynamicColors
                    _themePresetFlow.value = state.themePreset
                    _chatFontScaleFlow.value = normalizeChatFontScale(state.chatFontScale)
                }
            }
        }
    }

    /** Retrieves initialized secure storage or fails closed. */
    private fun requireSecureStorage(): SecretStore =
        secureStorage ?: throw IllegalStateException("AuthManager not initialized. Call init(context) first.")

    fun setWsAuthParam(param: String) {
        synchronized(monitor) {
            val selectedId = getSelectedProfileId()
            serverStore.update { state ->
                state.copy(
                    wsAuthParam = param,
                    connectionProfiles =
                        state.connectionProfiles.map { profile ->
                            if (profile.id == selectedId) {
                                profile.copy(wsAuthParam = param)
                            } else {
                                profile
                            }
                        },
                )
            }
        }
    }

    /**
     * WebSocket query parameter for the selected profile.
     *
     * New profiles persist this mode directly. Legacy profiles fall back to
     * the former global mode until they authenticate again.
     */
    fun getWsAuthParam(): String {
        val state = serverStore.getLatestState()
        val selectedId = state.selectedProfileId?.takeIf { it.isNotBlank() }
        val profileMode =
            state.connectionProfiles
                .firstOrNull { it.id == selectedId }
                ?.wsAuthParam
        return profileMode?.takeIf { it.isNotBlank() }
            ?: state.wsAuthParam.takeIf { it.isNotBlank() }
            ?: "token"
    }

    /** True when the selected profile uses dashboard cookie authentication. */
    override fun isGatedMode(): Boolean = getWsAuthParam() == "ticket"

    // ── Session Cookie (for gated/dashboard REST API) ────────────────────

    /**
     * In gated mode (basic auth), the dashboard authenticates REST API
     * requests via a dashboard session cookie, not via
     * `Authorization: Bearer`. The cookie is now owned by the shared
     * [CookieManager]/[PersistentCookieJar] (issue #470) which attaches it
     * automatically on every REST call, follows redirects, and persists it
     * encrypted. This accessor is a thin read-through to that store.
     */
    fun getSessionCookie(): String? = cookiePlatform?.getSessionCookie()

    fun setSessionCookie(cookie: String?) {
        cookiePlatform?.setSessionCookie(cookie, endpoint())
    }

    /**
     * Evict expired (non-session) cookies for the active server scope to
     * bound cookie growth (issue #470 step 7).
     */
    fun pruneServerCache() {
        cookiePlatform?.pruneServerCache()
    }

    // ── Database Master Password ─────────────────────────────────────────

    fun getDatabasePassword(): ByteArray =
        synchronized(databasePasswordLock) {
            val storage = requireSecureStorage()
            var dbPasswordBase64 = storage.getString(authSecretKey("db_password"))
            if (dbPasswordBase64 == null) {
                val newPassword = secureRandomBytes(DATABASE_PASSWORD_BYTES)
                dbPasswordBase64 = Base64.Default.encode(newPassword)
                storage.putString(authSecretKey("db_password"), dbPasswordBase64)
            }
            val decoded =
                try {
                    Base64.Default.decode(dbPasswordBase64)
                } catch (error: IllegalArgumentException) {
                    throw com.m57.hermescontrol.data.security.SecureBlobException(error)
                }
            if (decoded.size != DATABASE_PASSWORD_BYTES) {
                throw com.m57.hermescontrol.data.security.SecureBlobException()
            }
            decoded
        }

    // ── Connection Profiles ──────────────────────────────────────────────

    fun getConnectionProfiles(): List<ConnectionProfile> = serverStore.getLatestState().connectionProfiles

    fun saveConnectionProfiles(profiles: List<ConnectionProfile>) {
        synchronized(monitor) { serverStore.update { it.copy(connectionProfiles = profiles) } }
    }

    fun saveConnectionProfilesAndToken(
        profiles: List<ConnectionProfile>,
        profileId: String,
        token: String?,
    ) {
        synchronized(monitor) {
            serverStore.update { it.copy(connectionProfiles = profiles) }
            requireSecureStorage().putString(authSecretKey("token_$profileId"), token)
            if (getSelectedProfileId() == profileId) {
                cachedToken = token
                tokenInitialized = true
                publishTokenLocked(token)
            }
        }
    }

    fun saveConnectionProfilesAndSelect(
        profiles: List<ConnectionProfile>,
        profileId: String,
        token: String?,
    ) {
        synchronized(monitor) {
            serverStore.update { it.copy(connectionProfiles = profiles, selectedProfileId = profileId) }
            requireSecureStorage().putString(authSecretKey("token_$profileId"), token)
            cachedToken = token
            tokenInitialized = true
            syncCookieStoreForProfile(profileId)
            publishTokenLocked(token)
        }
    }

    /**
     * Guarantees the selected profile is never null (issue #478).
     *
     * - If a profile is already selected (default or otherwise), nothing is changed.
     * - If nothing is selected but other profiles exist, the first one is selected.
     * - If there are no profiles at all (fresh install / legacy standalone), a [DEFAULT_PROFILE_ID]
     *   profile is created from the current top-level host/port and selected.
     *
     * This never injects a Default profile into an existing user's profile list, and never
     * clobbers a user's explicit selection.
     */
    fun ensureDefaultProfile() {
        val state = serverStore.getLatestState()
        val hasDefault = state.connectionProfiles.any { it.id == DEFAULT_PROFILE_ID }
        val needsSelection = state.selectedProfileId.isNullOrBlank()

        if (!needsSelection && (hasDefault || state.connectionProfiles.isNotEmpty())) return

        if (state.connectionProfiles.isEmpty()) {
            // Fresh install / legacy standalone: create the Default profile and select it.
            serverStore.update { s ->
                s.copy(
                    connectionProfiles =
                        listOf(
                            ConnectionProfile(
                                id = DEFAULT_PROFILE_ID,
                                name = DEFAULT_PROFILE_NAME,
                                baseUrl = s.resolvedBaseUrl,
                            ),
                        ),
                    selectedProfileId = DEFAULT_PROFILE_ID,
                )
            }
            return
        }

        // Profiles exist but nothing is selected: pick the first one so selection is non-null.
        if (needsSelection) {
            serverStore.update { s -> s.copy(selectedProfileId = s.connectionProfiles.first().id) }
        }
    }

    /** Ensure a profile is selected (the default one if nothing else), so callers never see null. */
    fun ensureDefaultSelected() {
        ensureDefaultProfile()
        if (getSelectedProfileId().isNullOrBlank()) {
            setSelectedProfileId(DEFAULT_PROFILE_ID)
        }
    }

    /**
     * One-time migration: fold the legacy standalone ([KEY_LEGACY_TOKEN]) credentials into the
     * new default [ConnectionProfile]. Runs once per install, guarded by
     * [KEY_LEGACY_DEFAULT_MIGRATED].
     */
    private fun migrateLegacyDefaultIfNeeded(storage: SecretStore) {
        if (storage.getString(authSecretKey(KEY_LEGACY_DEFAULT_MIGRATED)) == "true") return
        val legacyToken = storage.getString(authSecretKey(KEY_LEGACY_TOKEN))
        ensureDefaultProfile()
        if (!legacyToken.isNullOrBlank()) {
            storage.putString(
                authSecretKey("token_$DEFAULT_PROFILE_ID"),
                legacyToken,
            )
        }
        storage.putString(authSecretKey(KEY_LEGACY_DEFAULT_MIGRATED), "true")
    }

    // ── Pinned Models ────────────────────────────────────────────────────

    fun getPinnedModels(): List<PinnedModel> = serverStore.getLatestState().pinnedModels

    fun savePinnedModels(pinned: List<PinnedModel>) {
        serverStore.update { it.copy(pinnedModels = pinned) }
    }

    // ── Pinned Sessions ──────────────────────────────────────────────────

    fun getPinnedSessionIds(profileId: String? = getSelectedProfileId()): List<String> {
        val resolvedProfileId = profileId ?: DEFAULT_PROFILE_ID
        return serverStore.getLatestState().pinnedSessionIdsByProfile[resolvedProfileId].orEmpty()
    }

    fun savePinnedSessionIds(
        pinnedSessionIds: List<String>,
        profileId: String? = getSelectedProfileId(),
    ) {
        val resolvedProfileId = profileId ?: DEFAULT_PROFILE_ID
        serverStore.update { state ->
            state.copy(
                pinnedSessionIdsByProfile =
                    state.pinnedSessionIdsByProfile +
                        (resolvedProfileId to pinnedSessionIds.distinct()),
            )
        }
    }

    fun clearPinnedSessionIds(profileId: String) {
        serverStore.update { state ->
            state.copy(
                pinnedSessionIdsByProfile =
                    state.pinnedSessionIdsByProfile - profileId,
            )
        }
    }

    fun getProfileToken(profileId: String): String? =
        requireSecureStorage().getString(authSecretKey("token_$profileId"))

    fun setProfileToken(
        profileId: String,
        token: String?,
    ) {
        synchronized(monitor) {
            requireSecureStorage().putString(authSecretKey("token_$profileId"), token)
            if (getSelectedProfileId() == profileId) {
                cachedToken = token
                tokenInitialized = true
                publishTokenLocked(token)
            }
        }
    }

    override fun getSelectedProfileId(): String? {
        val id = serverStore.getLatestState().selectedProfileId
        return if (id.isNullOrBlank()) null else id
    }

    fun setSelectedProfileId(id: String?) {
        synchronized(monitor) {
            serverStore.update { it.copy(selectedProfileId = id) }
            cachedToken = null
            tokenInitialized = false
            syncCookieStoreForProfile(id)
            publishTokenLocked(id?.takeIf(String::isNotBlank)?.let(::getProfileToken))
        }
    }

    data class CredentialBoundary(
        val profileId: String,
        val profileBacked: Boolean,
        val endpoint: ServerEndpoint,
        val gated: Boolean,
        val token: String?,
    )

    fun credentialBoundary(): CredentialBoundary =
        synchronized(monitor) {
            val state = serverStore.getLatestState()
            val selectedId = state.selectedProfileId?.takeIf(String::isNotBlank)
            val profileId = selectedId ?: DEFAULT_PROFILE_ID
            currentProfileBoundary(state, profileId)
                ?.copy(token = selectedId?.let(::getProfileToken))
                ?: CredentialBoundary(
                    profileId = profileId,
                    profileBacked = false,
                    endpoint = ServerEndpoint.parseForBuild(state.resolvedBaseUrl),
                    gated = state.wsAuthParam == "ticket",
                    token = selectedId?.let(::getProfileToken),
                )
        }

    /** Store a refresh result only while its captured profile boundary remains valid. */
    fun commitRefreshedToken(
        boundary: CredentialBoundary,
        token: String?,
    ): Boolean {
        return synchronized(monitor) {
            val state = serverStore.getLatestState()
            val currentProfile = currentProfileBoundary(state, boundary.profileId)
            if (!currentProfile.matches(boundary)) return@synchronized false

            requireSecureStorage().putString(authSecretKey("token_${boundary.profileId}"), token)
            val active =
                normalizedProfileId(state.selectedProfileId) == boundary.profileId &&
                    currentProfile.matches(boundary)
            if (active) {
                cachedToken = token
                tokenInitialized = true
                publishTokenLocked(token)
            }
            active
        }
    }

    private fun currentProfileBoundary(
        state: ServerStoreState,
        profileId: String,
    ): CredentialBoundary? {
        val profile = state.connectionProfiles.firstOrNull { it.id == profileId }
        if (profile == null && (profileId != DEFAULT_PROFILE_ID || state.connectionProfiles.isNotEmpty())) return null
        val endpoint = profile?.resolveBaseUrl(state.baseUrl) ?: state.resolvedBaseUrl
        val mode =
            profile?.wsAuthParam?.takeIf(String::isNotBlank)
                ?: state.wsAuthParam.takeIf(String::isNotBlank)
                ?: "token"
        return CredentialBoundary(
            profileId = profileId,
            profileBacked = profile != null,
            endpoint = ServerEndpoint.parseForBuild(endpoint),
            gated = mode == "ticket",
            token = null,
        )
    }

    private fun CredentialBoundary?.matches(other: CredentialBoundary): Boolean =
        this != null &&
            profileId == other.profileId &&
            profileBacked == other.profileBacked &&
            endpoint.baseUrl == other.endpoint.baseUrl &&
            gated == other.gated

    private fun normalizedProfileId(profileId: String?): String =
        profileId?.takeIf { it.isNotBlank() } ?: DEFAULT_PROFILE_ID

    private fun syncCookieStoreForProfile(profileId: String?) {
        val normalizedId = normalizedProfileId(profileId)
        val currentId = cookiePlatform?.currentServerOrNull() ?: return
        if (currentId != normalizedId) {
            cookiePlatform?.useStore(normalizedId)
        }
    }

    /** Publish the active token while the surrounding credential mutation still owns this monitor. */
    private fun publishTokenLocked(token: String?) {
        check(holdsMonitor(monitor))
        beforeTokenFlowPublicationForTest?.invoke()
        _tokenFlow.value = token
    }

    // ── Token ────────────────────────────────────────────────────────────

    @Volatile
    private var cachedToken: String? = null

    @Volatile
    private var tokenInitialized: Boolean = false

    // For testing purposes
    fun resetTokenCacheForTest() {
        synchronized(monitor) {
            cachedToken = null
            tokenInitialized = false
        }
    }

    // For testing purposes
    fun resetAuthStateForTest() {
        synchronized(monitor) {
            cachedToken = null
            tokenInitialized = false
            beforeTokenFlowPublicationForTest = null
            _serverStore = null
            secureStorage = null
            appScope?.let {
                try {
                    it.cancel()
                } catch (_: Exception) {
                }
            }
            appScope = null
        }
    }

    fun getToken(): String? {
        if (tokenInitialized) return cachedToken
        synchronized(monitor) {
            if (tokenInitialized) return cachedToken
            val selectedId = getSelectedProfileId()
            val token = if (selectedId != null) getProfileToken(selectedId) else null
            cachedToken = token
            tokenInitialized = true
            return token
        }
    }

    private fun getTokenInternal(storage: SecretStore): String? {
        val selectedId = serverStore.getLatestState().selectedProfileId?.takeIf { it.isNotBlank() }
        return if (selectedId != null) {
            storage.getString(authSecretKey("token_$selectedId"))
        } else {
            null
        }
    }

    fun setToken(token: String?) {
        synchronized(monitor) {
            val selectedId =
                getSelectedProfileId() ?: run {
                    ensureDefaultSelected()
                    DEFAULT_PROFILE_ID
                }
            requireSecureStorage().putString(authSecretKey("token_$selectedId"), token)
            cachedToken = token
            tokenInitialized = true
            publishTokenLocked(token)
        }
    }

    // ── Server endpoint ──────────────────────────────────────────────────

    fun getBaseUrl(): String = serverStore.getLatestState().resolvedBaseUrl

    fun endpoint(): ServerEndpoint =
        ServerEndpoint.parse(
            getBaseUrl(),
            CleartextPolicy.ALLOW_WITH_WARNING,
        )

    override fun endpointForBuild(): ServerEndpoint = ServerEndpoint.parseForBuild(getBaseUrl())

    fun setBaseUrl(baseUrl: String) {
        val normalized =
            ServerEndpoint.parse(
                baseUrl,
                CleartextPolicy.ALLOW_WITH_WARNING,
            ).baseUrl.toString()
        synchronized(monitor) {
            val selectedId =
                getSelectedProfileId() ?: run {
                    ensureDefaultSelected()
                    DEFAULT_PROFILE_ID
                }
            serverStore.update { state ->
                val profiles =
                    state.connectionProfiles.map { profile ->
                        if (profile.id == selectedId) {
                            profile.copy(
                                host = "",
                                port = 0,
                                baseUrl = normalized,
                            )
                        } else {
                            profile
                        }
                    }
                state.copy(
                    baseUrl = normalized,
                    connectionProfiles = profiles,
                )
            }
        }
    }

    /** Compatibility accessors for legacy pairing payloads. */
    fun getHost(): String = serverStore.getLatestState().resolvedHost

    fun getPort(): Int = serverStore.getLatestState().resolvedPort

    fun setHost(host: String) {
        val endpoint = endpoint()
        val normalizedHost = host.trim().removePrefix("[").removeSuffix("]")
        setBaseUrl(
            endpoint.baseUrl.newBuilder().host(normalizedHost).build().toString(),
        )
    }

    fun setPort(port: Int) {
        require(port in 1..65535) { "Port is out of range" }
        val endpoint = endpoint()
        setBaseUrl(endpoint.baseUrl.newBuilder().port(port).build().toString())
    }

    // ── Auto-reconnect ───────────────────────────────────────────────────

    override fun isAutoReconnect(): Boolean = serverStore.getLatestState().autoReconnect

    fun setAutoReconnect(enabled: Boolean) {
        serverStore.update { it.copy(autoReconnect = enabled) }
    }

    // ── Theme preference ──────────────────────────────────────────────────

    fun getThemePreference(): ThemePreference = serverStore.getLatestState().themePreference

    fun setThemePreference(theme: ThemePreference) {
        serverStore.update { it.copy(themePreference = theme) }
    }

    fun getChatFontScale(): Float = normalizeChatFontScale(serverStore.getLatestState().chatFontScale)

    fun setChatFontScale(scale: Float) {
        val normalized = normalizeChatFontScale(scale)
        _chatFontScaleFlow.value = normalized
        serverStore.update { it.copy(chatFontScale = normalized) }
    }

    fun normalizeChatFontScale(scale: Float): Float {
        if (!scale.isFinite()) return 1.0f
        return chatFontScaleStops.minBy { kotlin.math.abs(it - scale) }
    }

    fun isUseDynamicColors(): Boolean = serverStore.getLatestState().useDynamicColors

    fun setUseDynamicColors(value: Boolean) {
        serverStore.update { it.copy(useDynamicColors = value) }
    }

    fun getThemePreset(): ThemePreset = serverStore.getLatestState().themePreset

    fun setThemePreset(preset: ThemePreset) {
        serverStore.update { it.copy(themePreset = preset) }
    }

    /** Canonical build-allowed Retrofit base URL. */
    fun baseUrl(): String = endpointForBuild().baseUrl.toString()

    /** Canonical WebSocket URL with an encoded token or short-lived ticket. */
    override fun wsUrl(): String {
        return wsUrlWithCredential(getToken().orEmpty(), getWsAuthParam())
    }

    /** Build a WebSocket URL with a handshake-local credential. */
    override fun wsUrlWithCredential(
        credential: String,
        authParameter: String,
    ): String {
        return endpointForBuild().webSocketUrl(
            authParameter = authParameter,
            credential = credential,
        )
    }

    // ── Typing Effect ───────────────────────────────────────────────────

    fun isTypingEffectEnabled(): Boolean = serverStore.getLatestState().typingEffectEnabled

    fun setTypingEffectEnabled(enabled: Boolean) {
        serverStore.update { it.copy(typingEffectEnabled = enabled) }
    }

    // ── App display language ───────────────────────────────────────────
    // "system" follows the device locale; any other value is a BCP-47 code.

    fun getAppLanguage(): String = serverStore.getLatestState().appLanguage

    fun setAppLanguage(code: String) {
        serverStore.update { it.copy(appLanguage = code) }
    }

    fun getTypingEffectDelayMs(): Int = serverStore.getLatestState().typingEffectDelayMs

    fun setTypingEffectDelayMs(delayMs: Int) {
        serverStore.update { it.copy(typingEffectDelayMs = delayMs) }
    }
}
