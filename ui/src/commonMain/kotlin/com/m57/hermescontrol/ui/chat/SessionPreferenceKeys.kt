package com.m57.hermescontrol.ui.chat

/**
 * Preference keys that must not leak across profiles.
 *
 * A session id is only meaningful inside the profile that owns it: one global `last_session` key
 * made the chat resume the previous profile's conversation after a switch.
 */
object SessionPreferenceKeys {
    const val LEGACY_LAST_SESSION: String = "last_session"

    /** The remembered session for [profile]; unset profiles fall back to the launch profile. */
    fun lastSession(profile: String?): String = "last_session_" + (profile?.takeIf { it.isNotBlank() } ?: "default")
}
