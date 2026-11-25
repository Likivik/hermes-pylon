package com.m57.hermescontrol.data.local

/**
 * In-memory preferences, kept for tests and for callers that want a scratch
 * store. The real actual on this target is persistent — see
 * [rememberPreferencesStore].
 */
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
