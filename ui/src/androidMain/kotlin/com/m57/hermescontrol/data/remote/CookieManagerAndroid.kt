package com.m57.hermescontrol.data.remote

import android.content.Context
import com.m57.hermescontrol.data.security.SecretStore
import com.m57.hermescontrol.data.security.SecureStorage
import kotlin.concurrent.Volatile

/**
 * Android entry point for [CookieManager.initializeAndroidCookieStack].
 *
 * The manager itself is shared code and takes any [CookieStore]; only this
 * extension knows how to build the Keystore-sealed one from a [Context]. The
 * store factory is injectable so tests can substitute an in-memory store
 * without touching the real Keystore.
 */
object AndroidCookieStore {
    /**
     * How the Android cookie store seals its blobs. Tests point this at an
     * in-memory [SecretStore]; production leaves it at the Keystore-backed
     * default. Replaces the old `CookieManager.secureStorageFactory` var, which
     * lived on the shared object before the cookie port.
     */
    @Volatile
    var secureStorageFactory: (Context) -> SecretStore = ::SecureStorage
}

fun CookieManager.initializeAndroidCookieStack(
    context: Context,
    initialServerId: String = PersistentCookieJar.DEFAULT_SERVER_ID,
) {
    initialize(
        store = EncryptedCookieStore(context.applicationContext, AndroidCookieStore.secureStorageFactory),
        initialServerId = initialServerId,
    )
}
