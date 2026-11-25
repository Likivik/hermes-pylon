package com.m57.hermescontrol.data.local

import androidx.datastore.core.DataStoreFactory
import com.m57.hermescontrol.data.config.ServerStore
import com.m57.hermescontrol.data.config.serverStoreStorage
import com.m57.hermescontrol.data.remote.CookiePlatform
import com.m57.hermescontrol.data.remote.ServerEndpoint
import com.m57.hermescontrol.data.security.SecretStore
import com.m57.hermescontrol.util.HermesLog
import kotlinx.coroutines.CoroutineScope

/**
 * The `@JsFun` bridges below already swallow browser exceptions and report failure as
 * `false`/`null`. The silent path was that nobody looked at the `false`: a blocked or
 * full `sessionStorage` (private mode, quota, third-party cookie rules) made every write
 * fail while the app behaved as if it had saved — the same shape as the reload that
 * looked like a sign-out.
 *
 * The message names the operation, never the key or the value.
 */
private const val WEB_AUTH_TAG = "WebAuthPlatform"

private fun storeWarn(operation: String) {
    HermesLog.w(
        WEB_AUTH_TAG,
        "$operation failed: browser storage is blocked or full; the value will not survive a reload",
    )
}

/**
 * The browser side of [AuthPlatform].
 *
 * Profiles and the selected server persist in `localStorage` (DataStore's web
 * storage), so a reload keeps the server list. Secrets and cookies live in
 * `sessionStorage`: they survive a reload and die with the tab. Nothing goes to
 * disk, and nothing secret reaches `localStorage` — a browser token at rest is
 * readable by any script on the origin (issue #128). A WebCrypto-backed vault
 * that would survive a browser restart is Phase 4 of the multiplatform plan.
 */
internal class WebAuthPlatform : AuthPlatform {
    override fun createServerStore(scope: CoroutineScope): ServerStore =
        ServerStore(
            DataStoreFactory.create(
                storage = serverStoreStorage(),
                // No migrations: `ServerUrlMigration` currently lives in
                // androidMain, and a fresh browser install has no legacy
                // `server_store.json` to upgrade.
                migrations = emptyList(),
            ),
            scope,
        )

    override fun createSecretStore(): SecretStore = AuthManager.secretStoreFactory?.invoke() ?: TabSecretStore()

    override fun initializeCookieStore(initialServerId: String) {
        // Nothing to prepare: the cookie platform reads its slot lazily.
    }

    override val cookies: CookiePlatform = TabCookiePlatform()
}

/**
 * Secrets that live as long as the tab does.
 *
 * Backed by `sessionStorage` because `AuthManager.getToken()` reads this seam on
 * the boot path: an in-memory map meant every reload looked like a signed-out
 * user and sent the app back to the login screen.
 */
internal class TabSecretStore : SecretStore {
    private val values = mutableMapOf<String, String>()

    init {
        runCatching {
            val count = jsSessionLength()
            for (i in 0 until count) {
                val key = jsSessionKey(i) ?: continue
                if (!key.startsWith(PREFIX)) continue
                val value = jsSessionGet(key) ?: continue
                values[key.removePrefix(PREFIX)] = value
            }
        }
    }

    override fun getString(logicalKey: String): String? {
        values[logicalKey]?.let { return it }
        val stored = jsSessionGet(PREFIX + logicalKey) ?: return null
        values[logicalKey] = stored
        return stored
    }

    override fun putString(
        logicalKey: String,
        value: String?,
    ) {
        if (value == null) {
            values.remove(logicalKey)
            if (!jsSessionRemove(PREFIX + logicalKey)) storeWarn("secret remove")
        } else {
            values[logicalKey] = value
            if (!jsSessionSet(PREFIX + logicalKey, value)) storeWarn("secret write")
        }
    }

    override fun deletePrefix(prefix: String) {
        values.keys.filter { it.startsWith(prefix) }.forEach { values.remove(it) }
        runCatching {
            val count = jsSessionLength()
            for (i in 0 until count) {
                val key = jsSessionKey(i) ?: continue
                if (key.startsWith(PREFIX + prefix) && !jsSessionRemove(key)) storeWarn("secret delete-prefix")
            }
        }
    }

    private companion object {
        const val PREFIX = "pylon.secret."
    }
}

/** Session cookies per server id, in `sessionStorage` so a reload reuses them. */
internal class TabCookiePlatform : CookiePlatform {
    private val byServer = mutableMapOf<String, String>()
    private var current: String? = null

    override fun getSessionCookie(): String? {
        val id = current ?: return null
        byServer[id]?.let { return it }
        val stored = jsSessionGet(COOKIE_PREFIX + id) ?: return null
        byServer[id] = stored
        return stored
    }

    override fun setSessionCookie(
        cookie: String?,
        endpoint: ServerEndpoint,
    ) {
        val id = current ?: return
        if (cookie == null) {
            byServer.remove(id)
            if (!jsSessionRemove(COOKIE_PREFIX + id)) storeWarn("cookie remove")
        } else {
            byServer[id] = cookie
            if (!jsSessionSet(COOKIE_PREFIX + id, cookie)) storeWarn("cookie write")
        }
    }

    override fun pruneServerCache() {
        byServer.keys.retainAll(setOfNotNull(current))
    }

    override fun currentServerOrNull(): String? = current

    override fun useStore(serverId: String) {
        current = serverId
    }

    private companion object {
        const val COOKIE_PREFIX = "pylon.cookie."
    }
}

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

@JsFun("(k) => { try { window.sessionStorage.removeItem(k); return true } catch (e) { return false } }")
private external fun jsSessionRemove(key: String): Boolean

@JsFun("() => { try { return window.sessionStorage.length } catch (e) { return 0 } }")
private external fun jsSessionLength(): Int

@JsFun("(i) => { try { return window.sessionStorage.key(i) } catch (e) { return null } }")
private external fun jsSessionKey(index: Int): String?
