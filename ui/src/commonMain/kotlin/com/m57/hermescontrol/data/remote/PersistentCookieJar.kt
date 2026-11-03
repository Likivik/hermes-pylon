package com.m57.hermescontrol.data.remote

import com.m57.hermescontrol.util.ioDispatcher
import com.m57.hermescontrol.util.nowMillis
import com.m57.hermescontrol.util.synchronizedOn
import io.ktor.http.Url
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile

data class CookieCredentialBoundary(
    val serverId: String,
    val generation: Long,
    val acceptsCookies: Boolean,
)

/**
 * Persistent, server-scoped cookie jar.
 *
 * Design notes (issue #470):
 * - **Memory cache** keyed by cookie domain; request reads apply the RFC 6265
 *   host-only/domain/path/secure matching in [matches] (CookieMatching.kt).
 * - **Async persistence** — writes are dispatched to [storeScope] and never
 *   block the calling request thread.
 * - **Atomic server scoping** via [useStore] — callers (login, profile switch)
 *   flip the active server id; subsequent load/save target that scope. Guarded
 *   by [scopeMutex] so a swap can't race a mid-flight load.
 * - **Lazy load** — the first [useStore] for a server scope triggers a one-shot
 *   [CookieStore.load]. Reads never block on it: a read for a scope that has not
 *   finished loading returns what is in memory (empty at worst). Blocking reads
 *   would need `runBlocking`, and `runBlocking` does not exist on wasmJs.
 * - **Pruning** — [pruneServerCache] evicts expired cookies to bound growth.
 *
 * Dashboard access-cookie variants are explicitly retained across pruning
 * because their lifetime is owned server-side, not by client expiry.
 *
 * Thread-safety: every mutable field is guarded by [cacheLock] or
 * [credentialLock] through [synchronizedOn], which is a real lock on JVM/Android
 * and a no-op on the single-threaded web target. Lock order is always
 * credential → cache.
 */
class PersistentCookieJar(
    private val store: CookieStore,
    private val storeScope: CoroutineScope = CoroutineScope(SupervisorJob() + ioDispatcher),
    initialServerId: String = DEFAULT_SERVER_ID,
) {
    // serverId -> (cookie domain -> cookies)
    private val cache = mutableMapOf<String, MutableMap<String, MutableList<CookieHolder>>>()
    private val loadedScopes = mutableSetOf<String>()
    private val clearedScopes = mutableSetOf<String>()

    private val cacheLock = Any()
    private val credentialLock = Any()
    private val persistenceLock = Any()

    @Volatile
    private var allScopesCleared = false

    @Volatile
    private var credentialBoundary =
        CookieCredentialBoundary(
            serverId = initialServerId,
            generation = 0,
            acceptsCookies = true,
        )

    private val scopeMutex = Mutex()

    @Volatile
    private var persistenceTail: Job? = null

    /** Atomically switch the active server scope and ensure its cookies are loaded. */
    suspend fun useStore(serverId: String) {
        scopeMutex.withLock {
            synchronizedOn(credentialLock) {
                if (credentialBoundary.serverId != serverId) {
                    credentialBoundary =
                        CookieCredentialBoundary(
                            serverId = serverId,
                            generation = credentialBoundary.generation + 1,
                            acceptsCookies = cookieWritesEnabled(serverId),
                        )
                }
            }
            ensureLoaded(serverId)
        }
    }

    /** Current active server scope id. */
    fun currentServer(): String = credentialBoundary.serverId

    fun captureCredentialBoundary(): CookieCredentialBoundary = credentialBoundary

    fun acceptsCredentialBoundary(boundary: CookieCredentialBoundary): Boolean =
        boundary.acceptsCookies && boundary == credentialBoundary

    /** Begin a new explicit authentication attempt after a credential clear. */
    fun beginAuthentication() {
        synchronizedOn(credentialLock) {
            val current = credentialBoundary
            allScopesCleared = false
            clearedScopes.remove(current.serverId)
            credentialBoundary =
                current.copy(
                    generation = current.generation + 1,
                    acceptsCookies = true,
                )
        }
    }

    /**
     * Return the live [CookieHolder] with [name] from the active server scope, or
     * null if absent. Scans every host bucket; the session cookie is typically
     * host-scoped to the dashboard host.
     */
    fun getCookie(name: String): CookieHolder? {
        val serverId = credentialBoundary.serverId
        // No loaded-scope gate: the cache is authoritative for what this process
        // knows. A scope whose persisted cookies have not finished loading simply
        // looks empty until [useStore] completes.
        val buckets = bucketsFor(serverId) ?: return null
        for (bucket in buckets) {
            val match = synchronizedOn(bucket) { bucket.firstOrNull { it.name == name } }
            if (match != null) return match
        }
        return null
    }

    /**
     * Value of the active dashboard access cookie, including the `__Host-`
     * and `__Secure-` variants used by HTTPS deployments.
     */
    fun getSessionCookieValue(): String? =
        SESSION_COOKIE_NAMES.firstNotNullOfOrNull { name ->
            getCookie(name)?.value
        }

    fun saveFromResponse(
        url: Url,
        cookies: List<CookieHolder>,
    ) = saveFromResponse(credentialBoundary, url, cookies)

    fun saveFromResponse(
        boundary: CookieCredentialBoundary,
        url: Url,
        cookies: List<CookieHolder>,
    ) {
        if (cookies.isEmpty() || !acceptsCredentialBoundary(boundary)) return
        synchronizedOn(credentialLock) {
            val serverId = boundary.serverId
            for (cookie in cookies) {
                val domainKey = cookie.domain.removePrefix(".")
                val bucket =
                    synchronizedOn(cacheLock) {
                        val serverCookies = cache.getOrPut(serverId) { mutableMapOf() }
                        serverCookies.getOrPut(domainKey) { mutableListOf() }
                    }
                synchronizedOn(bucket) {
                    val idx =
                        bucket.indexOfFirst {
                            it.name == cookie.name &&
                                it.domain == cookie.domain &&
                                it.path == cookie.path
                        }
                    if (cookie.expiresAt <= nowMillis()) {
                        if (idx >= 0) bucket.removeAt(idx)
                    } else if (idx >= 0) {
                        bucket[idx] = cookie
                    } else {
                        bucket.add(cookie)
                    }
                }
            }
            // Persist the whole server scope asynchronously.
            persist(serverId)
        }
    }

    fun loadForRequest(url: Url): List<CookieHolder> {
        val serverId = credentialBoundary.serverId
        val buckets = bucketsFor(serverId) ?: return emptyList()
        val now = nowMillis()
        return buckets.flatMap { bucket ->
            synchronizedOn(bucket) {
                bucket.filter {
                    val isLegacyWildcard =
                        serverId == DEFAULT_SERVER_ID &&
                            it.domain == LEGACY_WILDCARD_DOMAIN
                    it.expiresAt > now && (it.matches(url) || isLegacyWildcard)
                }
            }
        }
    }

    private fun isLoaded(serverId: String): Boolean = synchronizedOn(cacheLock) { loadedScopes.contains(serverId) }

    private fun bucketsFor(serverId: String): List<MutableList<CookieHolder>>? =
        synchronizedOn(cacheLock) { cache[serverId]?.values?.toList() }

    private suspend fun ensureLoaded(serverId: String) {
        if (isLoaded(serverId)) return
        val skipLoad =
            synchronizedOn(cacheLock) {
                if (allScopesCleared || clearedScopes.contains(serverId)) {
                    cache.remove(serverId)
                    loadedScopes.add(serverId)
                    true
                } else {
                    false
                }
            }
        if (skipLoad) return

        val boundary = credentialBoundary
        val persisted = store.load(serverId)
        val byHost = mutableMapOf<String, MutableList<CookieHolder>>()
        for (cookie in persisted) {
            val host = cookie.domain.removePrefix(".")
            byHost.getOrPut(host) { mutableListOf() }.add(cookie)
        }
        synchronizedOn(cacheLock) {
            if (boundary != credentialBoundary ||
                allScopesCleared ||
                clearedScopes.contains(serverId)
            ) {
                return@synchronizedOn
            }
            // Merge rather than replace: a cookie saved before this load ran (an
            // explicit login racing start-up) must survive the persisted set.
            val live = cache.getOrPut(serverId) { mutableMapOf() }
            for ((host, bucket) in byHost) {
                val existing = live.getOrPut(host) { mutableListOf() }
                synchronizedOn(existing) {
                    for (cookie in bucket) {
                        val idx =
                            existing.indexOfFirst {
                                it.name == cookie.name &&
                                    it.domain == cookie.domain &&
                                    it.path == cookie.path
                            }
                        if (idx >= 0) existing[idx] = cookie else existing.add(cookie)
                    }
                }
            }
            loadedScopes.add(serverId)
        }
    }

    private fun persist(serverId: String) {
        val snapshot =
            synchronizedOn(cacheLock) {
                cache[serverId]?.values?.flatten()?.toList()
            } ?: return
        enqueuePersistence { store.save(serverId, snapshot) }
    }

    /** Preserve save/clear call order even when [storeScope] is multi-threaded. */
    private fun enqueuePersistence(operation: suspend () -> Unit) {
        synchronizedOn(persistenceLock) {
            val previous = persistenceTail
            persistenceTail =
                storeScope.launch {
                    previous?.join()
                    operation()
                }
        }
    }

    /**
     * Evict expired cookies for the active (or all) server scopes to prevent
     * unbounded growth. Session cookies without an explicit expiry carry a far
     * future sentinel, so no session-cookie exception is needed.
     */
    fun pruneServerCache(allScopes: Boolean = false) {
        val now = nowMillis()
        val scopeKeys =
            if (allScopes) {
                synchronizedOn(cacheLock) { cache.keys.toList() }
            } else {
                listOf(credentialBoundary.serverId)
            }
        for (scope in scopeKeys) {
            val hosts = synchronizedOn(cacheLock) { cache[scope]?.toList() } ?: continue
            for ((host, bucket) in hosts) {
                synchronizedOn(bucket) {
                    bucket.removeAll { it.expiresAt <= now }
                }
                if (synchronizedOn(bucket) { bucket.isEmpty() }) {
                    synchronizedOn(cacheLock) {
                        val live = cache[scope] ?: return@synchronizedOn
                        if (live[host] === bucket) live.remove(host)
                    }
                }
            }
            persist(scope)
        }
    }

    /** Clear the active server scope's in-memory + persisted cookies. */
    fun clearActive() {
        synchronizedOn(credentialLock) {
            val scope = credentialBoundary.serverId
            credentialBoundary =
                credentialBoundary.copy(
                    generation = credentialBoundary.generation + 1,
                    acceptsCookies = false,
                )
            synchronizedOn(cacheLock) {
                cache.remove(scope)
                clearedScopes.add(scope)
                loadedScopes.add(scope)
            }
            enqueuePersistence { store.clear(scope) }
        }
    }

    /** Clear every server scope (logout / full reset). */
    fun clearAll() {
        synchronizedOn(credentialLock) {
            synchronizedOn(cacheLock) {
                cache.clear()
                loadedScopes.clear()
                clearedScopes.clear()
            }
            allScopesCleared = true
            credentialBoundary =
                credentialBoundary.copy(
                    generation = credentialBoundary.generation + 1,
                    acceptsCookies = false,
                )
            enqueuePersistence { store.clearAll() }
        }
    }

    private fun cookieWritesEnabled(serverId: String): Boolean = !allScopesCleared && !clearedScopes.contains(serverId)

    companion object {
        const val DEFAULT_SERVER_ID = "default"
    }
}
