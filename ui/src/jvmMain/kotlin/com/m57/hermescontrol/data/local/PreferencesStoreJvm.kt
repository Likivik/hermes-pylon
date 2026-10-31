package com.m57.hermescontrol.data.local

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.util.prefs.Preferences

/**
 * Desktop preferences live in the JDK's preference tree, so they survive a
 * restart without inventing a file format.
 */
@Composable
actual fun rememberPreferencesStore(): PreferencesStore = remember { JvmPreferencesStore }

private object JvmPreferencesStore : PreferencesStore {
    private val node: Preferences = Preferences.userRoot().node("com/m57/hermescontrol")

    override fun getStringSet(key: String): Set<String> =
        getString(key)?.takeIf { it.isNotEmpty() }?.split(SET_SEPARATOR)?.toSet() ?: emptySet()

    override fun putStringSet(
        key: String,
        value: Set<String>,
    ) {
        putString(key, value.joinToString(SET_SEPARATOR))
    }

    override fun getString(key: String): String? = node.get(key, null)

    override fun putString(
        key: String,
        value: String,
    ) {
        node.put(key, value)
    }

    override fun remove(key: String) {
        node.remove(key)
    }

    override fun allStrings(): Map<String, String> =
        runCatching {
            node.keys().associateWith { key -> node.get(key, "") ?: "" }
        }.getOrDefault(emptyMap())

    private const val SET_SEPARATOR = "\u0001"
}
