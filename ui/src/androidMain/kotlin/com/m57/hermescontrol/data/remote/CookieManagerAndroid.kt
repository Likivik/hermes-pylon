package com.m57.hermescontrol.data.remote

import android.content.Context
import com.m57.hermescontrol.data.security.SecretStore
import com.m57.hermescontrol.data.security.SecureStorage

/**
 * Android entry point for [CookieManager.initializeAndroidCookieStack].
 *
 * The manager itself is shared code and takes any [CookieStore]; only this
 * extension knows how to build the Keystore-sealed one from a [Context]. The
 * store factory is injectable so tests can substitute an in-memory store
 * without touching the real Keystore.
 */
fun CookieManager.initializeAndroidCookieStack(
    context: Context,
    initialServerId: String = PersistentCookieJar.DEFAULT_SERVER_ID,
    secureStorageFactory: (Context) -> SecretStore = ::SecureStorage,
) {
    initialize(
        store = EncryptedCookieStore(context.applicationContext, secureStorageFactory),
        initialServerId = initialServerId,
    )
}
