package com.m57.hermescontrol.ui.authlogin

import com.m57.hermescontrol.data.remote.ServerEndpoint

/**
 * Performs the gated login POST and lets the platform capture the session
 * cookie it returns.
 *
 * The flow itself (validate the token, detect the auth mode, decide what to
 * store) is shared; only the cookie capture is per-target:
 *
 * - Android posts through `OkHttpProvider.probe`, whose persistent jar picks up
 *   the `Set-Cookie` and is what every later request sends.
 * - Web and desktop have no shared jar yet: the browser's own same-origin
 *   cookie store holds it (fetch sends same-origin cookies by default), and
 *   desktop has no persistence until the cookie tranche lands.
 *
 * @return the HTTP status code. Transport failures throw, so the caller can
 *   distinguish "the dashboard rejected the credentials" from "the dashboard
 *   is unreachable" the way it always could.
 */
internal expect suspend fun performPasswordLogin(
    endpoint: ServerEndpoint,
    jsonBody: String,
): Int
