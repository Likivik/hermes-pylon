package com.m57.hermescontrol.ui.sessions

import com.m57.hermescontrol.data.local.AuthManager

/**
 * The Android implementation of [SessionPinStore]: pinned ids live in the same
 * per-profile store as the rest of the auth state.
 *
 * This adapter is the only part of the pinning feature that needs `AuthManager`,
 * which is why it stays in `:app` while the logic it feeds moved to `:shared`.
 */
internal class AuthManagerSessionPinStore(
    private val profileId: String =
        AuthManager.getSelectedProfileId() ?: AuthManager.DEFAULT_PROFILE_ID,
) : SessionPinStore {
    override fun load(): List<String> = AuthManager.getPinnedSessionIds(profileId)

    override fun save(pinIds: List<String>) {
        AuthManager.savePinnedSessionIds(pinIds, profileId)
    }
}
