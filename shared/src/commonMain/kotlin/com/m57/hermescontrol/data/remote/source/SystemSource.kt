package com.m57.hermescontrol.data.remote.source

import com.m57.hermescontrol.data.model.ActionResponse
import com.m57.hermescontrol.data.model.ActionStatusResponse
import com.m57.hermescontrol.data.model.CheckpointsResponse
import com.m57.hermescontrol.data.model.CredentialPoolResponse
import com.m57.hermescontrol.data.model.CuratorResponse
import com.m57.hermescontrol.data.model.DebugShareResponse
import com.m57.hermescontrol.data.model.DoctorResponse
import com.m57.hermescontrol.data.model.HookResponse
import com.m57.hermescontrol.data.model.LearningGraphResponse
import com.m57.hermescontrol.data.model.MemoryResetRequest
import com.m57.hermescontrol.data.model.MemoryResetResponse
import com.m57.hermescontrol.data.model.MemoryResponse
import com.m57.hermescontrol.data.model.PortalResponse
import com.m57.hermescontrol.data.model.StatusResponse
import com.m57.hermescontrol.data.model.SystemStatsResponse
import com.m57.hermescontrol.data.model.UpdateCheckResponse
import com.m57.hermescontrol.data.model.UpdateReceiptResponse
import com.m57.hermescontrol.data.remote.GatewayResponse
import kotlinx.serialization.json.JsonObject

/**
 * What System needs from the gateway: 34 calls of the 150 it offers.
 *
 * Declared by the consumer, not by the gateway, so a fake for this screen implements 34 methods instead of
 * 150. `SystemSourceOf` is the production adapter; tests pass their own implementation.
 */
interface SystemSource {
    suspend fun addCredentialPoolEntry(body: Map<String, String>): GatewayResponse<JsonObject>

    suspend fun checkHermesUpdate(force: Boolean = false): GatewayResponse<UpdateCheckResponse>

    suspend fun createHook(body: JsonObject): GatewayResponse<JsonObject>

    suspend fun deleteHook(body: Map<String, String>): GatewayResponse<JsonObject>

    suspend fun downloadBackup(archive: String): GatewayResponse<ByteArray>

    suspend fun getActionStatus(
        name: String,
        lines: Int = 200,
    ): GatewayResponse<ActionStatusResponse>

    suspend fun getCheckpoints(): GatewayResponse<CheckpointsResponse>

    suspend fun getCredentialPool(): GatewayResponse<CredentialPoolResponse>

    suspend fun getCurator(): GatewayResponse<CuratorResponse>

    suspend fun getHooks(): GatewayResponse<HookResponse>

    suspend fun getLearningGraph(profile: String? = null): GatewayResponse<LearningGraphResponse>

    suspend fun getMemory(): GatewayResponse<MemoryResponse>

    suspend fun getPortal(): GatewayResponse<PortalResponse>

    suspend fun getStatus(): GatewayResponse<StatusResponse>

    suspend fun getSystemStats(): GatewayResponse<SystemStatsResponse>

    suspend fun getUpdateReceipt(): GatewayResponse<UpdateReceiptResponse>

    suspend fun pruneCheckpoints(): GatewayResponse<ActionResponse>

    suspend fun removeCredentialPoolEntry(
        provider: String,
        index: Int,
    ): GatewayResponse<JsonObject>

    suspend fun resetMemory(body: MemoryResetRequest): GatewayResponse<MemoryResetResponse>

    suspend fun restartGateway(): GatewayResponse<Unit>

    suspend fun runConfigMigrate(): GatewayResponse<ActionResponse>

    suspend fun runCurator(): GatewayResponse<ActionResponse>

    suspend fun runDebugShare(body: JsonObject): GatewayResponse<DebugShareResponse>

    suspend fun runDoctor(): GatewayResponse<DoctorResponse>

    suspend fun runDump(): GatewayResponse<ActionResponse>

    suspend fun runImport(body: JsonObject): GatewayResponse<ActionResponse>

    suspend fun runPromptSize(): GatewayResponse<ActionResponse>

    suspend fun runSecurityAudit(): GatewayResponse<ActionResponse>

    suspend fun setCuratorPaused(body: JsonObject): GatewayResponse<JsonObject>

    suspend fun startGateway(): GatewayResponse<Unit>

    suspend fun stopGateway(): GatewayResponse<Unit>

    suspend fun triggerBackup(): GatewayResponse<ActionResponse>

    suspend fun updateHermes(): GatewayResponse<ActionResponse>

    suspend fun updateSkillsFromHub(body: Map<String, String> = emptyMap()): GatewayResponse<ActionResponse>
}
