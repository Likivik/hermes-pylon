package com.m57.hermescontrol.data.remote.source

import com.m57.hermescontrol.data.model.Toolset
import com.m57.hermescontrol.data.model.ToolsetToggleRequest
import com.m57.hermescontrol.data.remote.GatewayResponse

/**
 * What Toolsets needs from the gateway: 2 calls of the 150 it offers.
 *
 * Declared by the consumer, not by the gateway, so a fake for this screen implements 2 methods instead of
 * 150. `DefaultToolsets` is the production adapter; tests pass their own implementation.
 */
interface Toolsets {
    suspend fun getToolsets(): GatewayResponse<List<Toolset>>

    suspend fun toggleToolset(
        name: String,
        body: ToolsetToggleRequest,
    ): GatewayResponse<Unit>
}
