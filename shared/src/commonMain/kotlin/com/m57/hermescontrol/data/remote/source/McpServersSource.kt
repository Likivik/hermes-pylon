package com.m57.hermescontrol.data.remote.source

import com.m57.hermescontrol.data.model.AddMcpServerRequest
import com.m57.hermescontrol.data.model.McpCatalogInstallRequest
import com.m57.hermescontrol.data.model.McpCatalogResponse
import com.m57.hermescontrol.data.model.McpOAuthFlowResponse
import com.m57.hermescontrol.data.model.McpServer
import com.m57.hermescontrol.data.model.McpServerTestResponse
import com.m57.hermescontrol.data.model.McpServerToggleRequest
import com.m57.hermescontrol.data.model.McpServersResponse
import com.m57.hermescontrol.data.remote.GatewayResponse
import kotlinx.serialization.json.JsonObject

/**
 * What McpServers needs from the gateway: 10 calls of the 150 it offers.
 *
 * Declared by the consumer, not by the gateway, so a fake for this screen implements 10 methods instead of
 * 150. `McpServersSourceOf` is the production adapter; tests pass their own implementation.
 */
interface McpServersSource {
    suspend fun addMcpServer(body: AddMcpServerRequest): GatewayResponse<McpServer>

    suspend fun authMcpServer(name: String): GatewayResponse<McpOAuthFlowResponse>

    suspend fun deleteMcpServer(name: String): GatewayResponse<Unit>

    suspend fun getMcpCatalog(): GatewayResponse<McpCatalogResponse>

    suspend fun getMcpOAuthFlowStatus(flowId: String): GatewayResponse<McpOAuthFlowResponse>

    suspend fun getMcpServers(): GatewayResponse<McpServersResponse>

    suspend fun installMcpCatalogEntry(body: McpCatalogInstallRequest): GatewayResponse<JsonObject>

    suspend fun testMcpServer(name: String): GatewayResponse<McpServerTestResponse>

    suspend fun toggleMcpServer(
        name: String,
        body: McpServerToggleRequest,
    ): GatewayResponse<Unit>

    suspend fun updateMcpServer(
        name: String,
        body: JsonObject,
    ): GatewayResponse<McpServer>
}
