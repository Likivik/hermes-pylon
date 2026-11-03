package com.m57.hermescontrol.data.remote

/**
 * Persistence for the cookie jar, one scope per dashboard server id.
 *
 * Android backs this with the Keystore-sealed `EncryptedCookieStore`; the other
 * targets supply their own. Everything here is `suspend` because the real
 * implementations hit disk or browser storage.
 */
interface CookieStore {
    suspend fun save(
        serverId: String,
        cookies: List<CookieHolder>,
    )

    suspend fun load(serverId: String): List<CookieHolder>

    suspend fun clear(serverId: String)

    suspend fun clearAll()
}
