package com.m57.hermescontrol.data.remote

import com.m57.hermescontrol.util.ioDispatcher
import com.m57.hermescontrol.util.nowMillis
import com.m57.hermescontrol.util.synchronizedOn
import io.ktor.http.Url
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

/**
 * Coordinator for the issue #470 cookie stack.
 *
 * Owns the single [PersistentCookieJar] instance and exposes:
 * - [cookieJar] — the jar every HTTP client reads cookies from.
 * - [initialize] — build the jar around a platform [CookieStore] (idempotent).
 *   Android supplies its Keystore-backed store through the
 *   `CookieManager.initializeAndroidCookieStack(context, …)` extension in `androidMain`.
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
    private val initLock = Any()

    @Volatile
    private var jar: PersistentCookieJar? = null

    val cookieJar: PersistentCookieJar
        get() = jar ?: error("CookieManager.initialize(...) must be called before use")

    fun isInitialized(): Boolean = jar != null

    /**
     * The `Cookie` header the persistent jar would send to [url], or `""`.
     *
     * Lives here rather than at the call sites so URL→cookie logic stays inside
     * the cookie stack.
     */
    fun cookieHeaderFor(url: String): String {
        val current = jar ?: return ""
        val target = runCatching { Url(url) }.getOrNull() ?: return ""
        return current.loadForRequest(target).joinToString("; ") { "${it.name}=${it.value}" }
    }

    /** Current scope when initialized; null during startup or test teardown. */
    fun currentServerOrNull(): String? = jar?.currentServer()

    /**
     * Build the jar around [store]. Concurrent callers are serialised; the first
     * one wins and later calls are no-ops, so start-up paths can call this
     * without coordinating.
     */
    fun initialize(
        store: CookieStore,
        initialServerId: String = PersistentCookieJar.DEFAULT_SERVER_ID,
    ) {
        if (jar != null) return
        synchronizedOn(initLock) {
            if (jar == null) {
                val createdJar = PersistentCookieJar(store, scope, initialServerId)
                jar = createdJar
                // Eagerly load the initial scope off the caller thread so the
                // first REST call finds cookies already in memory.
                scope.launch { createdJar.useStore(initialServerId) }
            }
        }
    }

    /**
     * Switch the active server scope and load its persisted cookies.
     *
     * The scope id and credential generation flip synchronously; the persisted
     * load happens on [scope], because blocking a caller on it would need
     * `runBlocking`, which does not exist on wasmJs. A request issued in the
     * same tick as the swap may therefore see an empty jar for the new scope —
     * callers that must not (the login flow) await [PersistentCookieJar.useStore]
     * directly via the jar.
     */
    fun useStore(serverId: String) {
        val current = jar ?: return
        scope.launch { current.useStore(serverId) }
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
        val current = jar ?: return
        if (rawValue.isNullOrBlank()) {
            current.clearActive()
            return
        }
        current.beginAuthentication()
        current.saveFromResponse(
            Url(endpoint.baseUrl.toString()),
            listOf(
                CookieHolder(
                    name = SESSION_COOKIE_NAME,
                    value = rawValue,
                    // Session lifetime is owned by the dashboard, not by
                    // client-side expiry.
                    expiresAt = nowMillis() + 10L * 365 * 24 * 60 * 60 * 1000,
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
    }
}
