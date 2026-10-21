@file:Suppress("ktlint:standard:filename")

package com.m57.hermescontrol.data.local

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Android persists UI preferences in the same `hermes_rail` file the rail uses. */
@Composable
actual fun rememberPreferencesStore(): PreferencesStore {
    val context = LocalContext.current
    return remember(context) { AndroidPreferencesStore(context.applicationContext) }
}

/** Exposed so non-composable Android code (the ChatViewModel factory) can share it. */
class AndroidPreferencesStore(
    context: Context,
) : PreferencesStore {
    private val appContext = context.applicationContext
    private val prefs by lazy { appContext.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE) }

    override fun getStringSet(key: String): Set<String> = prefs.getStringSet(key, emptySet()).orEmpty()

    override fun putStringSet(
        key: String,
        value: Set<String>,
    ) {
        prefs.edit().putStringSet(key, value.toSet()).apply()
    }

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(
        key: String,
        value: String,
    ) {
        prefs.edit().putString(key, value).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    override fun allStrings(): Map<String, String> =
        prefs.all.mapNotNull { (key, value) -> (value as? String)?.let { key to it } }.toMap()

    private companion object {
        const val PREF_FILE = "hermes_rail"
    }
}
