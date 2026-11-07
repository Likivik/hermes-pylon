package com.m57.hermescontrol.ui.authlogin

import com.m57.hermescontrol.data.remote.HermesHttpConfig
import com.m57.hermescontrol.data.remote.ServerEndpoint
import com.m57.hermescontrol.data.remote.createHermesHttpClient
import io.ktor.client.request.post
import kotlinx.browser.window

/**
 * Web logout: tell the server, then go back to the HTML sign-in page.
 *
 * Both halves matter. The POST is the only thing that can clear the HttpOnly
 * session cookie — the app cannot touch it, and `authStore.setSessionCookie(null)`
 * only forgets the copy in memory. Without it a refresh walks straight back in.
 *
 * The redirect goes to `login.html` rather than the app's own sign-in screen:
 * that screen is a canvas, so it cannot hold a password manager, and reaching it
 * here would leave the user typing a password into a field their manager cannot
 * fill.
 *
 * A failed POST still redirects. Staying on a screen the user just asked to
 * leave is worse than an extra sign-in.
 */
internal actual suspend fun performLogout(endpoint: ServerEndpoint) {
    runCatching {
        createHermesHttpClient(HermesHttpConfig.Probe).use { client ->
            client.post(endpoint.baseUrl.resolve("auth/logout").toString()).status
        }
    }
    window.location.replace("login.html")
}
