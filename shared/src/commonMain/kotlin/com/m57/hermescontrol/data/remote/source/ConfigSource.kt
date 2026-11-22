package com.m57.hermescontrol.data.remote.source

import com.m57.hermescontrol.data.model.ConfigSchemaResponse
import com.m57.hermescontrol.data.model.ConfigUpdateRequest
import com.m57.hermescontrol.data.model.RawConfigResponse
import com.m57.hermescontrol.data.model.UpdateRawConfigRequest
import com.m57.hermescontrol.data.remote.GatewayResponse
import kotlinx.serialization.json.JsonElement

/**
 * What Config needs from the gateway: 6 calls of the 150 it offers.
 *
 * Declared by the consumer, not by the gateway, so a fake for this screen implements 6 methods instead of
 * 150. `ConfigSourceOf` is the production adapter; tests pass their own implementation.
 */
interface ConfigSource {
    suspend fun getConfig(): GatewayResponse<Map<String, kotlinx.serialization.json.JsonElement>>

    suspend fun getConfigDefaults(): GatewayResponse<Map<String, kotlinx.serialization.json.JsonElement>>

    suspend fun getConfigSchema(): GatewayResponse<ConfigSchemaResponse>

    suspend fun getRawConfig(): GatewayResponse<RawConfigResponse>

    suspend fun updateConfig(body: ConfigUpdateRequest): GatewayResponse<Unit>

    suspend fun updateRawConfig(body: UpdateRawConfigRequest): GatewayResponse<Unit>
}
