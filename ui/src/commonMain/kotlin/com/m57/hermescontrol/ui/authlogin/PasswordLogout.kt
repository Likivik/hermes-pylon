package com.m57.hermescontrol.ui.authlogin

import com.m57.hermescontrol.data.remote.ServerEndpoint

/**
 * Ends the session on the platform that owns it.
 *
 * Clearing the in-memory store is not a logout on the web: the session is an
 * HttpOnly cookie the browser holds, and only the server can clear it. Without
 * this call the app returns to its own sign-in screen while the cookie stays
 * valid, so the next reload walks straight back in — and the HTML sign-in page
 * never appears.
 *
 * Android and desktop keep the session in the store itself, so their actual is
 * a no-op; the store clear is the whole logout there.
 */
internal expect suspend fun performLogout(endpoint: ServerEndpoint)
