package com.m57.hermescontrol.data.remote.source

import com.m57.hermescontrol.data.model.AchievementsResponse
import com.m57.hermescontrol.data.model.RecentUnlock
import com.m57.hermescontrol.data.remote.GatewayResponse

/**
 * What the achievements screen needs from the gateway: 4 calls of the 150 it offers.
 *
 * Declared by the consumer, not by the gateway, so a fake for this screen implements 4 methods. `AchievementsSourceOf`
 * is the production adapter.
 */
interface AchievementsSource {
    suspend fun getAchievements(): GatewayResponse<AchievementsResponse>

    suspend fun getRecentUnlocks(): GatewayResponse<List<RecentUnlock>>

    suspend fun rescanAchievements(): GatewayResponse<AchievementsResponse>

    suspend fun resetAchievementState(): GatewayResponse<Unit>
}
