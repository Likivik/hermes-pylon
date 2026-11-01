package com.m57.hermescontrol.data.local

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val PREFIX = "pylon.prefs."

@JsFun("(k) => { try { return window.localStorage.getItem(k) } catch (e) { return null } }")
private external fun jsGet(key: String): String?

@JsFun("(k, v) => { try { window.localStorage.setItem(k, v); return true } catch (e) { return false } }")
private external fun jsSet(
    key: String,
    value: String,
): Boolean

@JsFun("(k) => { try { window.localStorage.removeItem(k); return true } catch (e) { return false } }")
private external fun jsRemove(key: String): Boolean

@JsFun(
    """() => {
        const out = {};
        try {
            for (let i = 0; i < window.localStorage.length; i++) {
                const k = window.localStorage.key(i);
                if (k && k.startsWith('pylon.prefs.')) out[k] = window.localStorage.getItem(k);
            }
        } catch (e) {}
        return JSON.stringify(out);
    }""",
)
private external fun jsAllPrefixed(): String

/**
 * `localStorage`-backed preferences: they survive a reload, which is what a
 * browser client needs. A private-mode browser that refuses storage degrades to
 * a no-op rather than throwing.
 */
@Composable
actual fun rememberPreferencesStore(): PreferencesStore = remember { LocalStoragePreferencesStore }

private object LocalStoragePreferencesStore : PreferencesStore {
    override fun getStringSet(key: String): Set<String> =
        getString(key)?.takeIf { it.isNotEmpty() }?.split(SET_SEPARATOR)?.toSet() ?: emptySet()

    override fun putStringSet(
        key: String,
        value: Set<String>,
    ) {
        putString(key, value.joinToString(SET_SEPARATOR))
    }

    override fun getString(key: String): String? = jsGet(PREFIX + key)

    override fun putString(
        key: String,
        value: String,
    ) {
        jsSet(PREFIX + key, value)
    }

    override fun remove(key: String) {
        jsRemove(PREFIX + key)
    }

    override fun allStrings(): Map<String, String> =
        runCatching {
            Json
                .parseToJsonElement(jsAllPrefixed())
                .jsonObject
                .mapValues { (_, v) -> v.jsonPrimitive.content }
                .mapKeys { (k, _) -> k.removePrefix(PREFIX) }
        }.getOrDefault(emptyMap())

    private const val SET_SEPARATOR = "\u0001"
}
