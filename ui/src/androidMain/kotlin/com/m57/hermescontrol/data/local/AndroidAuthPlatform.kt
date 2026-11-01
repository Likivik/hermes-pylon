package com.m57.hermescontrol.data.local

import android.content.Context
import androidx.datastore.core.DataStoreFactory
import com.m57.hermescontrol.data.config.ServerStore
import com.m57.hermescontrol.data.config.ServerStoreMigration
import com.m57.hermescontrol.data.config.ServerUrlMigration
import com.m57.hermescontrol.data.config.serverStoreStorage
import com.m57.hermescontrol.data.remote.CookieManager
import com.m57.hermescontrol.data.remote.CookiePlatform
import com.m57.hermescontrol.data.remote.ServerEndpoint
import com.m57.hermescontrol.data.security.SecretStore
import kotlinx.coroutines.CoroutineScope

/** The Android side of [AuthPlatform]: `filesDir`, Keystore and the encrypted cookie store. */
internal class AndroidAuthPlatform(
    context: Context,
) : AuthPlatform {
    private val appContext = context.applicationContext ?: context

    override fun createServerStore(scope: CoroutineScope): ServerStore =
        ServerStore(
            DataStoreFactory.create(
                storage = serverStoreStorage(appContext),
                migrations = listOf(ServerStoreMigration(appContext), ServerUrlMigration()),
            ),
            scope,
        )

    // Goes through the same factory AuthManager exposes, so existing test
    // overrides (and any future one) keep working unchanged.
    override fun createSecretStore(): SecretStore = AuthManager.secureStorageFactory(appContext)

    override fun initializeCookieStore(initialServerId: String) {
        CookieManager.initialize(appContext, initialServerId = initialServerId)
    }

    override val cookies: CookiePlatform =
        object : CookiePlatform {
            override fun getSessionCookie(): String? = CookieManager.getSessionCookie()

            override fun setSessionCookie(
                cookie: String?,
                endpoint: ServerEndpoint,
            ) {
                CookieManager.setSessionCookie(cookie, endpoint)
            }

            override fun pruneServerCache() = CookieManager.pruneServerCache()

            override fun currentServerOrNull(): String? = CookieManager.currentServerOrNull()

            override fun useStore(serverId: String) = CookieManager.useStore(serverId)
        }
}
