package com.m57.hermescontrol.ui.authlogin

import com.m57.hermescontrol.data.remote.ServerEndpoint

/**
 * The session lives in the store on these targets — no server-side logout to
 * perform, and no page to return to. Clearing the store is the logout.
 */
internal actual suspend fun performLogout(endpoint: ServerEndpoint) = Unit
