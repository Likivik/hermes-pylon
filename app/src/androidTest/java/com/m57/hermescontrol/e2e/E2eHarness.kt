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
            wsAuthParam = "ticket",
        )
        AuthManager.saveConnectionProfilesAndToken(
            profiles = listOf(profile),
            profileId = AuthManager.DEFAULT_PROFILE_ID,
            token = "e2e-token",
        )
    }
}
