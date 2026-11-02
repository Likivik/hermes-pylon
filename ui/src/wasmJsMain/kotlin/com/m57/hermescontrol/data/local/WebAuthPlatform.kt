package com.m57.hermescontrol.data.local

import androidx.datastore.core.DataStoreFactory
import com.m57.hermescontrol.data.config.ServerStore
import com.m57.hermescontrol.data.config.serverStoreStorage
import com.m57.hermescontrol.data.remote.CookiePlatform
import com.m57.hermescontrol.data.remote.ServerEndpoint
import com.m57.hermescontrol.data.security.SecretStore
import kotlinx.coroutines.CoroutineScope

/**
 * The browser side of [AuthPlatform].
 *
 * Profiles and the selected server persist in `localStorage` (DataStore's web
 * storage), so a reload keeps the server list. Secrets and cookies are
 * session-only: nothing is written to disk, and a reload signs the user out.
 * That is the current policy decision, not an omission — a WebCrypto-backed
 * vault is Phase 4 of the multiplatform plan.
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

    override fun createSecretStore(): SecretStore = AuthManager.secretStoreFactory?.invoke() ?: SessionSecretStore()

    override fun initializeCookieStore(initialServerId: String) {
        // Nothing to prepare: the cookie store below is in-memory.
    }

    override val cookies: CookiePlatform = SessionCookiePlatform()
}

/** Secrets that live only as long as the tab does. */
internal class SessionSecretStore : SecretStore {
    private val values = mutableMapOf<String, String>()

    override fun getString(logicalKey: String): String? = values[logicalKey]

    override fun putString(
        logicalKey: String,
        value: String?,
    ) {
        if (value == null) values.remove(logicalKey) else values[logicalKey] = value
    }

    override fun deletePrefix(prefix: String) {
        values.keys.filter { it.startsWith(prefix) }.forEach(values::remove)
    }
}

/** Session cookies in a map, per server id, mirroring the Android store's shape. */
internal class SessionCookiePlatform : CookiePlatform {
    private val byServer = mutableMapOf<String, String>()
    private var current: String? = null

    override fun getSessionCookie(): String? = current?.let(byServer::get)

    override fun setSessionCookie(
        cookie: String?,
        endpoint: ServerEndpoint,
    ) {
        val id = current ?: return
        if (cookie == null) byServer.remove(id) else byServer[id] = cookie
    }

    override fun pruneServerCache() {
        byServer.keys.retainAll(setOfNotNull(current))
    }

    override fun currentServerOrNull(): String? = current

    override fun useStore(serverId: String) {
        current = serverId
    }
}
