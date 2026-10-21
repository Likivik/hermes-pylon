package com.m57.hermescontrol.data.local

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * Desktop has no settings backend wired up yet.
 *
 * Preferences live for the lifetime of the process only.
 */
@Composable
actual fun rememberPreferencesStore(): PreferencesStore = remember { InMemoryPreferencesStore() }

/** Desktop keeps preferences in memory. */
class InMemoryPreferencesStore : PreferencesStore {
    private val sets = mutableMapOf<String, Set<String>>()
    private val strings = mutableMapOf<String, String>()

    override fun getStringSet(key: String): Set<String> = sets[key].orEmpty()

    override fun putStringSet(
        key: String,
        value: Set<String>,
    ) {
        sets[key] = value.toSet()
    }

    override fun getString(key: String): String? = strings[key]

    override fun putString(
        key: String,
        value: String,
    ) {
        strings[key] = value
    }

    override fun remove(key: String) {
        sets.remove(key)
        strings.remove(key)
    }

    override fun allStrings(): Map<String, String> = strings.toMap()
}
