// Modified from Hy4ri/hermes-mobile for this fork; see NOTICE.

package com.m57.hermescontrol.data.remote

import android.content.Context
import com.m57.hermescontrol.data.security.SecretStore
import com.m57.hermescontrol.data.security.SecureStorage
import com.m57.hermescontrol.util.ioDispatcher
import io.ktor.http.Url
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Coordinator for the issue #470 cookie stack.
 *
 * Owns the single [PersistentCookieJar] instance and exposes:
 * - [cookieJar] — inject into every OkHttp client (REST + WS + probe).
 * - [initialize] — build the encrypted store + jar (idempotent).
 * - [useStore] — atomically switch the active server scope.
 * - [pruneServerCache] / [clearAll] — lifecycle/maintenance.
 * - [getSessionCookie] / [setSessionCookie] — backward-compatible accessors
 *   used by [com.m57.hermescontrol.data.local.AuthManager] and the login flow.
 *
 * A single-value accessor remains for compatibility with migrated sessions;
 * internally it resolves the dashboard access cookie from the active scope,
 * including HTTPS prefix variants.
 */
object CookieManager {
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    @Volatile private var jar: PersistentCookieJar? = null

    @Volatile
    var secureStorageFactory: (Context) -> SecretStore = ::SecureStorage

    val cookieJar: PersistentCookieJar
        get() = jar ?: error("CookieManager.initialize(context) must be called before use")

    fun isInitialized(): Boolean = jar != null

    /**
     * The `Cookie` header the persistent jar would send to [url], or `""`.
     *
     * Lives here rather than at the call sites so URL→cookie logic stays inside
     * the cookie stack. `GatewayFileClient` used to build an `okhttp3.HttpUrl`
     * just to ask this question, which was the only reason it imported OkHttp.
     */
    fun cookieHeaderFor(url: String): String {
        val current = jar ?: return ""
        val target = runCatching { Url(url) }.getOrNull() ?: return ""
        return current.loadForRequest(target).joinToString("; ") { "${it.name}=${it.value}" }
    }

    /** Current scope when initialized; null during startup or test teardown. */
    fun currentServerOrNull(): String? = jar?.currentServer()

    fun initialize(
        context: Context,
        initialServerId: String = PersistentCookieJar.DEFAULT_SERVER_ID,
    ) {
        if (jar != null) return
        synchronized(this) {
            if (jar != null) return
            val store = EncryptedCookieStore(context.applicationContext, secureStorageFactory)
            val createdJar = PersistentCookieJar(store, scope, initialServerId)
            jar = createdJar
            // Eagerly load the initial scope off the caller thread so the
            // first REST call is instant without blocking app startup.
            scope.launch { createdJar.useStore(initialServerId) }
        }
    }

    /** Switch the active server scope and load its persisted cookies. */
    fun useStore(serverId: String) {
        val j = jar ?: return
        runBlocking(scope.coroutineContext) { j.useStore(serverId) }
    }

    /** Read the current dashboard access-cookie value (or null). */
    fun getSessionCookie(): String? = jar?.getSessionCookieValue()

    /** Open a new cookie credential generation for an explicit login. */
    fun beginAuthentication() {
        jar?.beginAuthentication()
    }

    /**
     * Set the session cookie for the active scope and canonical endpoint.
     * HTTPS endpoints produce Secure cookies that cannot be sent over HTTP.
     */
    fun setSessionCookie(
        rawValue: String?,
        endpoint: ServerEndpoint,
    ) {
        val j = jar ?: return
        if (rawValue.isNullOrBlank()) {
            j.clearActive()
            return
        }
        j.beginAuthentication()
        j.saveFromResponse(
            Url(endpoint.baseUrl.toString()),
            listOf(
                CookieHolder(
                    name = SESSION_COOKIE_NAME,
                    value = rawValue,
                    // Session lifetime is owned by the dashboard, not by
                    // client-side expiry.
                    expiresAt = System.currentTimeMillis() + 10L * 365 * 24 * 60 * 60 * 1000,
                    domain = endpoint.baseUrl.host,
                    path = endpoint.baseUrl.encodedPath,
                    secure = endpoint.baseUrl.isHttps,
                    httpOnly = true,
                    hostOnly = true,
                ),
            ),
        )
    }

    /** Evict expired (non-session) cookies for the active scope. */
    fun pruneServerCache() {
        jar?.pruneServerCache(allScopes = false)
    }

    /** Wipe all cookies across all server scopes (logout / full reset). */
    fun clearAll() {
        jar?.clearAll()
    }

    // ── Test seam ────────────────────────────────────────────────────────

    /**
     * Replace the backing jar with [jar] (used by unit tests to inject a
     * [FakePersistentCookieJar]). Not for production use.
     */
    @Suppress("unused")
    fun setJarForTest(jar: PersistentCookieJar?) {
        this.jar = jar
    }

    /** Reset to uninitialized state (tests only). */
    @Suppress("unused")
    fun resetForTest() {
        jar = null
        secureStorageFactory = ::SecureStorage
    }
}
