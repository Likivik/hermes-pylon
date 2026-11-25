package com.m57.hermescontrol.data.remote.source

import com.m57.hermescontrol.data.model.AgentPluginInstallBody
import com.m57.hermescontrol.data.model.PluginProvidersPutRequest
import com.m57.hermescontrol.data.model.PluginsHubResponse
import com.m57.hermescontrol.data.remote.GatewayResponse

/**
 * What Plugins needs from the gateway: 9 calls of the 150 it offers.
 *
 * Declared by the consumer, not by the gateway, so a fake for this screen implements 9 methods instead of
 * 150. `DefaultPlugins` is the production adapter; tests pass their own implementation.
 */
interface Plugins {
    suspend fun disablePlugin(name: String): GatewayResponse<Unit>

    suspend fun enablePlugin(name: String): GatewayResponse<Unit>

    suspend fun getPlugins(): GatewayResponse<PluginsHubResponse>

    suspend fun installPlugin(body: AgentPluginInstallBody): GatewayResponse<Unit>

    suspend fun rescanPlugins(): GatewayResponse<Unit>

    suspend fun savePluginProviders(body: PluginProvidersPutRequest): GatewayResponse<Unit>

    suspend fun setPluginVisibility(
        name: String,
        body: Map<String, Boolean>,
    ): GatewayResponse<Unit>

    suspend fun uninstallPlugin(name: String): GatewayResponse<Unit>

    suspend fun updatePlugin(name: String): GatewayResponse<Unit>
}
