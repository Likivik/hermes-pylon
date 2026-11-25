package com.m57.hermescontrol.data.local

import com.m57.hermescontrol.data.config.ServerStore
import com.m57.hermescontrol.data.remote.CookiePlatform
import com.m57.hermescontrol.data.security.SecretStore
import kotlinx.coroutines.CoroutineScope

/**
 * Everything `AuthManager` needs from the device, gathered in one value.
 *
 * Android supplies a `Context`-backed implementation (DataStore over
 * `filesDir`, Keystore-backed secret storage, the encrypted cookie store). The
 * desktop and browser targets supply their own — which is what lets the manager
 * itself stop taking a `Context`.
 */
interface AuthPlatform {
    /** The persisted server/profile state, including any legacy migration. */
    fun createServerStore(scope: CoroutineScope): ServerStore

    /** Encrypted storage for tokens and the database password. */
    fun createSecretStore(): SecretStore

    /** Prepares the encrypted cookie store for [initialServerId]. */
    fun initializeCookieStore(initialServerId: String)

    /** Runtime cookie-store operations, available after [initializeCookieStore]. */
    val cookies: CookiePlatform
}
