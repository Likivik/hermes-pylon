package com.m57.hermescontrol.data.remote.source

import com.m57.hermescontrol.data.model.AnalyticsResponse
import com.m57.hermescontrol.data.model.ModelsAnalyticsResponse
import com.m57.hermescontrol.data.remote.GatewayResponse

/**
 * What Analytics needs from the gateway: 2 calls of the 150 it offers.
 *
 * Declared by the consumer, not by the gateway, so a fake for this screen implements 2 methods instead of
 * 150. `DefaultAnalytics` is the production adapter; tests pass their own implementation.
 */
interface Analytics {
    suspend fun getAnalytics(
        days: Int,
        profile: String? = null,
    ): GatewayResponse<AnalyticsResponse>

    suspend fun getModelsAnalytics(
        days: Int,
        profile: String? = null,
    ): GatewayResponse<ModelsAnalyticsResponse>
}
