package com.m57.hermescontrol.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** The web target keeps slash usage for the session only. */
class InMemorySlashUsageStore : SlashUsageStore {
    private val state = MutableStateFlow<Map<String, Map<String, Int>>>(emptyMap())

    override fun counts(profileId: String): Flow<Map<String, Int>> = state.map { it[profileId].orEmpty() }

    override suspend fun recordUse(
        profileId: String,
        command: String,
    ) {
        state.value =
            state.value + (
                profileId to
                    (state.value[profileId].orEmpty() + (command to ((state.value[profileId]?.get(command) ?: 0) + 1)))
            )
    }
}
