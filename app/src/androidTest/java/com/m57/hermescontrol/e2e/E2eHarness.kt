package com.m57.hermescontrol.e2e

import com.m57.hermescontrol.data.config.ConnectionProfile
import com.m57.hermescontrol.data.local.AuthManager

/**
 * Seeds the app's real auth store so MainActivity lands on ChatScreen
 * (the rail) instead of LandingScreen. androidTest runs in the app
 * process, so AuthManager's public save API is directly callable and
 * tokenFlow reactively re-navigates any already-launched activity.
 */
object E2eHarness {
    fun seedServerProfile(baseUrl: String = "http://127.0.0.1:9119/") {
        val profile = ConnectionProfile(
            id = AuthManager.DEFAULT_PROFILE_ID,
            name = "E2E",
            baseUrl = baseUrl,
            // "token", NOT "ticket": a ticket profile puts openSocket into gated mode,
            // which POSTs api/auth/ws-ticket to a server that does not exist in the
            // test env, yielding TRANSIENT_FAILURE and a deferred socket — the WS
            // override is never reached. "token" takes the non-gated path where
            // the offline refresh is best-effort, so the override URL wins.
        )
        AuthManager.saveConnectionProfilesAndToken(
            profiles = listOf(profile),
            profileId = AuthManager.DEFAULT_PROFILE_ID,
            token = "e2e-token",
        )
    }
}
