package com.m57.hermescontrol.ui.sessions

import com.m57.hermescontrol.AppEnvironment
import com.m57.hermescontrol.data.local.AuthDefaults
import com.m57.hermescontrol.data.local.authStore

/**
 * Default [SessionPinStore]: pinned ids live in the same per-profile store as the
 * rest of the auth state, reached through the platform [authStore].
 */
class AuthManagerSessionPinStore(
    private val profileId: String =
        AppEnvironment.current.auth.getSelectedProfileId() ?: AuthDefaults.PROFILE_ID,
) : SessionPinStore {
    override fun load(): List<String> = authStore.getPinnedSessionIds(profileId)

    override fun save(pinIds: List<String>) {
        authStore.savePinnedSessionIds(pinIds, profileId)
    }
}
