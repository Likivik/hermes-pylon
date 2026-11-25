package com.m57.hermescontrol.data.remote.source

import com.m57.hermescontrol.data.model.EnvVarConfig
import com.m57.hermescontrol.data.model.EnvVarDeleteRequest
import com.m57.hermescontrol.data.model.EnvVarRevealRequest
import com.m57.hermescontrol.data.model.EnvVarRevealResponse
import com.m57.hermescontrol.data.model.EnvVarUpdate
import com.m57.hermescontrol.data.remote.GatewayResponse

/**
 * What Keys needs from the gateway: 5 calls of the 150 it offers.
 *
 * Declared by the consumer, not by the gateway, so a fake for this screen implements 5 methods instead of
 * 150. `DefaultKeys` is the production adapter; tests pass their own implementation.
 */
interface Keys {
    suspend fun deleteEnvVar(request: EnvVarDeleteRequest): GatewayResponse<Unit>

    suspend fun getEnvVars(): GatewayResponse<Map<String, EnvVarConfig>>

    suspend fun restartGateway(): GatewayResponse<Unit>

    suspend fun revealEnvVar(request: EnvVarRevealRequest): GatewayResponse<EnvVarRevealResponse>

    suspend fun updateEnvVar(body: EnvVarUpdate): GatewayResponse<Unit>
}
