package com.m57.hermescontrol.data.remote.source

import com.m57.hermescontrol.data.model.ActionResponse
import com.m57.hermescontrol.data.model.SaveSkillContentRequest
import com.m57.hermescontrol.data.model.Skill
import com.m57.hermescontrol.data.model.SkillContentResponse
import com.m57.hermescontrol.data.model.SkillHubInstallRequest
import com.m57.hermescontrol.data.model.SkillHubSearchResponse
import com.m57.hermescontrol.data.model.SkillHubUninstallRequest
import com.m57.hermescontrol.data.model.ToggleSkillRequest
import com.m57.hermescontrol.data.remote.GatewayResponse

/**
 * What Skills needs from the gateway: 9 calls of the 150 it offers.
 *
 * Declared by the consumer, not by the gateway, so a fake for this screen implements 9 methods instead of
 * 150. `SkillsSourceOf` is the production adapter; tests pass their own implementation.
 */
interface SkillsSource {
    suspend fun getSkillContent(name: String): GatewayResponse<SkillContentResponse>

    suspend fun getSkills(): GatewayResponse<List<Skill>>

    suspend fun installHubSkill(body: SkillHubInstallRequest): GatewayResponse<ActionResponse>

    suspend fun previewHubSkill(identifier: String): GatewayResponse<SkillContentResponse>

    suspend fun saveSkillContent(body: SaveSkillContentRequest): GatewayResponse<Unit>

    suspend fun searchSkillsHub(
        q: String,
        source: String? = null,
        limit: Int? = null,
    ): GatewayResponse<SkillHubSearchResponse>

    suspend fun toggleSkill(body: ToggleSkillRequest): GatewayResponse<Unit>

    suspend fun uninstallHubSkill(body: SkillHubUninstallRequest): GatewayResponse<ActionResponse>

    suspend fun updateSkillsFromHub(body: Map<String, String> = emptyMap()): GatewayResponse<ActionResponse>
}
