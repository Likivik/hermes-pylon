package com.m57.hermescontrol.data.local

import androidx.compose.runtime.Composable

/**
 * Small key-value store for UI preferences that are not worth a database.
 *
 * Android binds this to `SharedPreferences`; desktop and web keep an in-memory map,
 * so preferences reset when the app restarts. That is the honest behaviour until a
 * multiplatform settings backend exists — silently pretending to persist would be
 * worse.
 *
 * The surface mirrors what the rail already uses: sets, single strings, removal, and
 * a full read used to enumerate per-session icons.
 */
interface PreferencesStore {
    fun getStringSet(key: String): Set<String>

    fun putStringSet(
        key: String,
        value: Set<String>,
    )

    fun getString(key: String): String?

    fun putString(
        key: String,
        value: String,
    )

    fun remove(key: String)

    /** Every stored string value, keyed by name. Non-string entries are ignored. */
    fun allStrings(): Map<String, String>
}

/** The preferences store available in the current composition. */
@Composable
expect fun rememberPreferencesStore(): PreferencesStore
