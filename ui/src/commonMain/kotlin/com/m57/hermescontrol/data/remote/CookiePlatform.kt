package com.m57.hermescontrol.data.remote

/**
 * The cookie-store operations `AuthManager` performs at runtime.
 *
 * Android delegates to the encrypted `CookieManager`; the desktop and browser
 * targets supply their own implementation. Setup (`initialize`) stays on
 * [com.m57.hermescontrol.data.local.AuthPlatform] — this port is only what the
 * manager touches after start-up.
 */
interface CookiePlatform {
    fun getSessionCookie(): String?

    fun setSessionCookie(
        cookie: String?,
        endpoint: ServerEndpoint,
    )

    fun pruneServerCache()

    fun currentServerOrNull(): String?

    fun useStore(serverId: String)
}
