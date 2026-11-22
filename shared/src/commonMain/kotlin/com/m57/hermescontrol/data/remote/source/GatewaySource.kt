package com.m57.hermescontrol.data.remote.source

import com.m57.hermescontrol.data.model.StatusResponse
import com.m57.hermescontrol.data.remote.GatewayResponse

/**
 * What Gateway needs from the gateway: 1 calls of the 150 it offers.
 *
 * Declared by the consumer, not by the gateway, so a fake for this screen implements 1 methods instead of
 * 150. `GatewaySourceOf` is the production adapter; tests pass their own implementation.
 */
interface GatewaySource {
    suspend fun getStatus(): GatewayResponse<StatusResponse>
}
