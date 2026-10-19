package com.m57.hermescontrol.data.local

import kotlinx.coroutines.flow.Flow

/**
 * Per-profile counts of slash commands, used to rank the command palette.
 *
 * Android persists them through DataStore; desktop and web keep them in memory for
 * the session. Ranking is a convenience, not conversation state, so losing it on
 * relaunch costs nothing.
 */
interface SlashUsageStore {
    fun counts(profileId: String): Flow<Map<String, Int>>

    suspend fun recordUse(
        profileId: String,
        command: String,
    )
}
