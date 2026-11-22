package com.m57.hermescontrol.data.remote.source

import com.m57.hermescontrol.data.model.AchievementsResponse
import com.m57.hermescontrol.data.model.ActionResponse
import com.m57.hermescontrol.data.model.ActionStatusResponse
import com.m57.hermescontrol.data.model.ActiveProfileResponse
import com.m57.hermescontrol.data.model.AddMcpServerRequest
import com.m57.hermescontrol.data.model.AgentPluginInstallBody
import com.m57.hermescontrol.data.model.AnalyticsResponse
import com.m57.hermescontrol.data.model.AuxiliaryModelsResponse
import com.m57.hermescontrol.data.model.CheckpointsResponse
import com.m57.hermescontrol.data.model.ConfigSchemaResponse
import com.m57.hermescontrol.data.model.ConfigUpdateRequest
import com.m57.hermescontrol.data.model.CreateCronJobRequest
import com.m57.hermescontrol.data.model.CreateProfileRequest
import com.m57.hermescontrol.data.model.CreateTaskBody
import com.m57.hermescontrol.data.model.CreateWebhookRequest
import com.m57.hermescontrol.data.model.CredentialPoolResponse
import com.m57.hermescontrol.data.model.CronJob
import com.m57.hermescontrol.data.model.CuratorResponse
import com.m57.hermescontrol.data.model.DebugShareResponse
import com.m57.hermescontrol.data.model.DeleteWebhookResponse
import com.m57.hermescontrol.data.model.DoctorResponse
import com.m57.hermescontrol.data.model.EnvVarConfig
import com.m57.hermescontrol.data.model.EnvVarDeleteRequest
import com.m57.hermescontrol.data.model.EnvVarRevealRequest
import com.m57.hermescontrol.data.model.EnvVarRevealResponse
import com.m57.hermescontrol.data.model.EnvVarUpdate
import com.m57.hermescontrol.data.model.HookResponse
import com.m57.hermescontrol.data.model.KanbanBoardResponse
import com.m57.hermescontrol.data.model.KanbanBoardsResponse
import com.m57.hermescontrol.data.model.KanbanTask
import com.m57.hermescontrol.data.model.LearningGraphResponse
import com.m57.hermescontrol.data.model.LogResponse
import com.m57.hermescontrol.data.model.ManagedDirectoryCreate
import com.m57.hermescontrol.data.model.ManagedFileActionResponse
import com.m57.hermescontrol.data.model.ManagedFileDelete
import com.m57.hermescontrol.data.model.ManagedFilesListResponse
import com.m57.hermescontrol.data.model.McpCatalogInstallRequest
import com.m57.hermescontrol.data.model.McpCatalogResponse
import com.m57.hermescontrol.data.model.McpOAuthFlowResponse
import com.m57.hermescontrol.data.model.McpServer
import com.m57.hermescontrol.data.model.McpServerTestResponse
import com.m57.hermescontrol.data.model.McpServerToggleRequest
import com.m57.hermescontrol.data.model.McpServersResponse
import com.m57.hermescontrol.data.model.MemoryResetRequest
import com.m57.hermescontrol.data.model.MemoryResetResponse
import com.m57.hermescontrol.data.model.MemoryResponse
import com.m57.hermescontrol.data.model.MessagingPlatformResponse
import com.m57.hermescontrol.data.model.MessagingPlatformTestResult
import com.m57.hermescontrol.data.model.MessagingPlatformUpdate
import com.m57.hermescontrol.data.model.MoaConfigResponse
import com.m57.hermescontrol.data.model.ModelAssignmentRequest
import com.m57.hermescontrol.data.model.ModelAssignmentResponse
import com.m57.hermescontrol.data.model.ModelOptionsResponse
import com.m57.hermescontrol.data.model.ModelsAnalyticsResponse
import com.m57.hermescontrol.data.model.OAuthPollResponse
import com.m57.hermescontrol.data.model.OAuthProvidersResponse
import com.m57.hermescontrol.data.model.OAuthStartResponse
import com.m57.hermescontrol.data.model.OAuthSubmitRequest
import com.m57.hermescontrol.data.model.OAuthSubmitResponse
import com.m57.hermescontrol.data.model.PluginProvidersPutRequest
import com.m57.hermescontrol.data.model.PluginsHubResponse
import com.m57.hermescontrol.data.model.PortalResponse
import com.m57.hermescontrol.data.model.ProfileSoulResponse
import com.m57.hermescontrol.data.model.ProfilesResponse
import com.m57.hermescontrol.data.model.PruneRequest
import com.m57.hermescontrol.data.model.RawConfigResponse
import com.m57.hermescontrol.data.model.RecentUnlock
import com.m57.hermescontrol.data.model.SaveSkillContentRequest
import com.m57.hermescontrol.data.model.SessionListResponse
import com.m57.hermescontrol.data.model.SessionStatsResponse
import com.m57.hermescontrol.data.model.SetActiveProfileRequest
import com.m57.hermescontrol.data.model.Skill
import com.m57.hermescontrol.data.model.SkillContentResponse
import com.m57.hermescontrol.data.model.SkillHubInstallRequest
import com.m57.hermescontrol.data.model.SkillHubSearchResponse
import com.m57.hermescontrol.data.model.SkillHubUninstallRequest
import com.m57.hermescontrol.data.model.StatusResponse
import com.m57.hermescontrol.data.model.SystemStatsResponse
import com.m57.hermescontrol.data.model.TelegramOnboardingApplyRequest
import com.m57.hermescontrol.data.model.TelegramOnboardingApplyResponse
import com.m57.hermescontrol.data.model.TelegramOnboardingStartRequest
import com.m57.hermescontrol.data.model.TelegramOnboardingStartResponse
import com.m57.hermescontrol.data.model.TelegramOnboardingStatusResponse
import com.m57.hermescontrol.data.model.ToggleSkillRequest
import com.m57.hermescontrol.data.model.Toolset
import com.m57.hermescontrol.data.model.ToolsetToggleRequest
import com.m57.hermescontrol.data.model.UpdateCheckResponse
import com.m57.hermescontrol.data.model.UpdateCronJobRequest
import com.m57.hermescontrol.data.model.UpdateProfileDescriptionRequest
import com.m57.hermescontrol.data.model.UpdateProfileModelRequest
import com.m57.hermescontrol.data.model.UpdateProfileSoulRequest
import com.m57.hermescontrol.data.model.UpdateRawConfigRequest
import com.m57.hermescontrol.data.model.UpdateReceiptResponse
import com.m57.hermescontrol.data.model.WebhookSubscription
import com.m57.hermescontrol.data.model.WebhookToggleSubscriptionRequest
import com.m57.hermescontrol.data.model.WebhooksResponse
import com.m57.hermescontrol.data.model.WebhooksToggleRequest
import com.m57.hermescontrol.data.remote.GatewayResponse
import com.m57.hermescontrol.data.remote.HermesGatewayApi
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Production adapters: one per screen interface, one line per call.
 *
 * Pure delegation on purpose. Kotlin forbids default values on an overriding function, so the defaults live on the
 * interface and the adapter passes every argument explicitly. Deleting them from `HermesGatewayApi` instead would
 * change its public API for 60 calls the app relies on.
 */

class AnalyticsSourceOf(private val api: HermesGatewayApi) : AnalyticsSource {
    override suspend fun getAnalytics(
        days: Int,
        profile: String?,
    ): GatewayResponse<AnalyticsResponse> = api.getAnalytics(days, profile)

    override suspend fun getModelsAnalytics(
        days: Int,
        profile: String?,
    ): GatewayResponse<ModelsAnalyticsResponse> = api.getModelsAnalytics(days, profile)
}

class BotsSourceOf(private val api: HermesGatewayApi) : BotsSource {
    override suspend fun getProfiles(): GatewayResponse<ProfilesResponse> = api.getProfiles()
}

class ChannelsSourceOf(private val api: HermesGatewayApi) : ChannelsSource {
    override suspend fun applyTelegramOnboarding(
        pairingId: String,
        body: TelegramOnboardingApplyRequest,
    ): GatewayResponse<TelegramOnboardingApplyResponse> = api.applyTelegramOnboarding(pairingId, body)

    override suspend fun cancelTelegramOnboarding(pairingId: String): GatewayResponse<Unit> =
        api.cancelTelegramOnboarding(pairingId)

    override suspend fun configurePlatform(
        platformId: String,
        config: MessagingPlatformUpdate,
    ): GatewayResponse<Unit> = api.configurePlatform(platformId, config)

    override suspend fun getMessagingPlatforms(profile: String?): GatewayResponse<MessagingPlatformResponse> =
        api.getMessagingPlatforms(profile)

    override suspend fun getTelegramOnboardingStatus(
        pairingId: String,
    ): GatewayResponse<TelegramOnboardingStatusResponse> = api.getTelegramOnboardingStatus(pairingId)

    override suspend fun removeMessagingPlatform(platformId: String): GatewayResponse<Unit> =
        api.removeMessagingPlatform(platformId)

    override suspend fun restartGateway(): GatewayResponse<Unit> = api.restartGateway()

    override suspend fun startTelegramOnboarding(
        body: TelegramOnboardingStartRequest,
    ): GatewayResponse<TelegramOnboardingStartResponse> = api.startTelegramOnboarding(body)

    override suspend fun testMessagingPlatform(platformId: String): GatewayResponse<MessagingPlatformTestResult> =
        api.testMessagingPlatform(platformId)
}

class ConfigSourceOf(private val api: HermesGatewayApi) : ConfigSource {
    override suspend fun getConfig(): GatewayResponse<Map<String, kotlinx.serialization.json.JsonElement>> =
        api.getConfig()

    override suspend fun getConfigDefaults(): GatewayResponse<Map<String, kotlinx.serialization.json.JsonElement>> =
        api.getConfigDefaults()

    override suspend fun getConfigSchema(): GatewayResponse<ConfigSchemaResponse> = api.getConfigSchema()

    override suspend fun getRawConfig(): GatewayResponse<RawConfigResponse> = api.getRawConfig()

    override suspend fun updateConfig(body: ConfigUpdateRequest): GatewayResponse<Unit> = api.updateConfig(body)

    override suspend fun updateRawConfig(body: UpdateRawConfigRequest): GatewayResponse<Unit> =
        api.updateRawConfig(body)
}

class ConnectSourceOf(private val api: HermesGatewayApi) : ConnectSource {
    override suspend fun getStatus(): GatewayResponse<StatusResponse> = api.getStatus()
}

class CronJobsSourceOf(private val api: HermesGatewayApi) : CronJobsSource {
    override suspend fun createCronJob(body: CreateCronJobRequest): GatewayResponse<CronJob> = api.createCronJob(body)

    override suspend fun deleteCronJob(id: String): GatewayResponse<Unit> = api.deleteCronJob(id)

    override suspend fun getCronJob(id: String): GatewayResponse<CronJob> = api.getCronJob(id)

    override suspend fun getCronJobs(): GatewayResponse<List<CronJob>> = api.getCronJobs()

    override suspend fun pauseCronJob(id: String): GatewayResponse<Unit> = api.pauseCronJob(id)

    override suspend fun resumeCronJob(id: String): GatewayResponse<Unit> = api.resumeCronJob(id)

    override suspend fun triggerCronJob(id: String): GatewayResponse<Unit> = api.triggerCronJob(id)

    override suspend fun updateCronJob(
        id: String,
        body: UpdateCronJobRequest,
    ): GatewayResponse<CronJob> = api.updateCronJob(id, body)
}

class GatewaySourceOf(private val api: HermesGatewayApi) : GatewaySource {
    override suspend fun getStatus(): GatewayResponse<StatusResponse> = api.getStatus()
}

class KanbanSourceOf(private val api: HermesGatewayApi) : KanbanSource {
    override suspend fun createKanbanTask(
        board: String?,
        task: CreateTaskBody,
    ): GatewayResponse<KanbanTask> = api.createKanbanTask(board, task)

    override suspend fun getKanbanBoard(): GatewayResponse<KanbanBoardResponse> = api.getKanbanBoard()

    override suspend fun getKanbanBoards(): GatewayResponse<KanbanBoardsResponse> = api.getKanbanBoards()

    override suspend fun switchKanbanBoard(slug: String): GatewayResponse<Unit> = api.switchKanbanBoard(slug)

    override suspend fun updateKanbanTask(
        taskId: String,
        body: Map<String, String?>,
    ): GatewayResponse<Unit> = api.updateKanbanTask(taskId, body)
}

class KeysSourceOf(private val api: HermesGatewayApi) : KeysSource {
    override suspend fun deleteEnvVar(request: EnvVarDeleteRequest): GatewayResponse<Unit> = api.deleteEnvVar(request)

    override suspend fun getEnvVars(): GatewayResponse<Map<String, EnvVarConfig>> = api.getEnvVars()

    override suspend fun restartGateway(): GatewayResponse<Unit> = api.restartGateway()

    override suspend fun revealEnvVar(request: EnvVarRevealRequest): GatewayResponse<EnvVarRevealResponse> =
        api.revealEnvVar(request)

    override suspend fun updateEnvVar(body: EnvVarUpdate): GatewayResponse<Unit> = api.updateEnvVar(body)
}

class McpServersSourceOf(private val api: HermesGatewayApi) : McpServersSource {
    override suspend fun addMcpServer(body: AddMcpServerRequest): GatewayResponse<McpServer> = api.addMcpServer(body)

    override suspend fun authMcpServer(name: String): GatewayResponse<McpOAuthFlowResponse> = api.authMcpServer(name)

    override suspend fun deleteMcpServer(name: String): GatewayResponse<Unit> = api.deleteMcpServer(name)

    override suspend fun getMcpCatalog(): GatewayResponse<McpCatalogResponse> = api.getMcpCatalog()

    override suspend fun getMcpOAuthFlowStatus(flowId: String): GatewayResponse<McpOAuthFlowResponse> =
        api.getMcpOAuthFlowStatus(flowId)

    override suspend fun getMcpServers(): GatewayResponse<McpServersResponse> = api.getMcpServers()

    override suspend fun installMcpCatalogEntry(body: McpCatalogInstallRequest): GatewayResponse<JsonObject> =
        api.installMcpCatalogEntry(body)

    override suspend fun testMcpServer(name: String): GatewayResponse<McpServerTestResponse> = api.testMcpServer(name)

    override suspend fun toggleMcpServer(
        name: String,
        body: McpServerToggleRequest,
    ): GatewayResponse<Unit> = api.toggleMcpServer(name, body)

    override suspend fun updateMcpServer(
        name: String,
        body: JsonObject,
    ): GatewayResponse<McpServer> = api.updateMcpServer(name, body)
}

class ModelSourceOf(private val api: HermesGatewayApi) : ModelSource {
    override suspend fun getActiveProfile(): GatewayResponse<ActiveProfileResponse> = api.getActiveProfile()

    override suspend fun getAuxiliaryModels(): GatewayResponse<AuxiliaryModelsResponse> = api.getAuxiliaryModels()

    override suspend fun getMoaModels(): GatewayResponse<MoaConfigResponse> = api.getMoaModels()

    override suspend fun getModelOptions(
        refresh: Boolean,
        includeUnconfigured: Boolean,
    ): GatewayResponse<ModelOptionsResponse> = api.getModelOptions(refresh, includeUnconfigured)

    override suspend fun getProfiles(): GatewayResponse<ProfilesResponse> = api.getProfiles()

    override suspend fun saveMoaModels(body: MoaConfigResponse): GatewayResponse<MoaConfigResponse> =
        api.saveMoaModels(body)

    override suspend fun setModelAssignment(body: ModelAssignmentRequest): GatewayResponse<ModelAssignmentResponse> =
        api.setModelAssignment(body)

    override suspend fun updateProfileModel(
        name: String,
        body: UpdateProfileModelRequest,
    ): GatewayResponse<Unit> = api.updateProfileModel(name, body)
}

class PluginsSourceOf(private val api: HermesGatewayApi) : PluginsSource {
    override suspend fun disablePlugin(name: String): GatewayResponse<Unit> = api.disablePlugin(name)

    override suspend fun enablePlugin(name: String): GatewayResponse<Unit> = api.enablePlugin(name)

    override suspend fun getPlugins(): GatewayResponse<PluginsHubResponse> = api.getPlugins()

    override suspend fun installPlugin(body: AgentPluginInstallBody): GatewayResponse<Unit> = api.installPlugin(body)

    override suspend fun rescanPlugins(): GatewayResponse<Unit> = api.rescanPlugins()

    override suspend fun savePluginProviders(body: PluginProvidersPutRequest): GatewayResponse<Unit> =
        api.savePluginProviders(body)

    override suspend fun setPluginVisibility(
        name: String,
        body: Map<String, Boolean>,
    ): GatewayResponse<Unit> = api.setPluginVisibility(name, body)

    override suspend fun uninstallPlugin(name: String): GatewayResponse<Unit> = api.uninstallPlugin(name)

    override suspend fun updatePlugin(name: String): GatewayResponse<Unit> = api.updatePlugin(name)
}

class ProfilesSourceOf(private val api: HermesGatewayApi) : ProfilesSource {
    override suspend fun createProfile(body: CreateProfileRequest): GatewayResponse<Unit> = api.createProfile(body)

    override suspend fun getActiveProfile(): GatewayResponse<ActiveProfileResponse> = api.getActiveProfile()

    override suspend fun getModelOptions(
        refresh: Boolean,
        includeUnconfigured: Boolean,
    ): GatewayResponse<ModelOptionsResponse> = api.getModelOptions(refresh, includeUnconfigured)

    override suspend fun getProfileSoul(name: String): GatewayResponse<ProfileSoulResponse> = api.getProfileSoul(name)

    override suspend fun getProfiles(): GatewayResponse<ProfilesResponse> = api.getProfiles()

    override suspend fun getSkills(): GatewayResponse<List<Skill>> = api.getSkills()

    override suspend fun searchSkillsHub(
        q: String,
        source: String?,
        limit: Int?,
    ): GatewayResponse<SkillHubSearchResponse> = api.searchSkillsHub(q, source, limit)

    override suspend fun setActiveProfile(body: SetActiveProfileRequest): GatewayResponse<Unit> =
        api.setActiveProfile(body)

    override suspend fun updateProfileDescription(
        name: String,
        body: UpdateProfileDescriptionRequest,
    ): GatewayResponse<Unit> = api.updateProfileDescription(name, body)

    override suspend fun updateProfileModel(
        name: String,
        body: UpdateProfileModelRequest,
    ): GatewayResponse<Unit> = api.updateProfileModel(name, body)

    override suspend fun updateProfileSoul(
        name: String,
        body: UpdateProfileSoulRequest,
    ): GatewayResponse<Unit> = api.updateProfileSoul(name, body)
}

class ProvidersSourceOf(private val api: HermesGatewayApi) : ProvidersSource {
    override suspend fun cancelOAuthSession(sessionId: String): GatewayResponse<Unit> =
        api.cancelOAuthSession(sessionId)

    override suspend fun disconnectOAuthProvider(providerId: String): GatewayResponse<Unit> =
        api.disconnectOAuthProvider(providerId)

    override suspend fun getOAuthProviders(): GatewayResponse<OAuthProvidersResponse> = api.getOAuthProviders()

    override suspend fun pollOAuthSession(
        providerId: String,
        sessionId: String,
    ): GatewayResponse<OAuthPollResponse> = api.pollOAuthSession(providerId, sessionId)

    override suspend fun startOAuthLogin(providerId: String): GatewayResponse<OAuthStartResponse> =
        api.startOAuthLogin(providerId)

    override suspend fun submitOAuthCode(
        providerId: String,
        body: OAuthSubmitRequest,
    ): GatewayResponse<OAuthSubmitResponse> = api.submitOAuthCode(providerId, body)
}

class SettingsSourceOf(private val api: HermesGatewayApi) : SettingsSource {
    override suspend fun getStatus(): GatewayResponse<StatusResponse> = api.getStatus()
}

class SkillsSourceOf(private val api: HermesGatewayApi) : SkillsSource {
    override suspend fun getSkillContent(name: String): GatewayResponse<SkillContentResponse> =
        api.getSkillContent(name)

    override suspend fun getSkills(): GatewayResponse<List<Skill>> = api.getSkills()

    override suspend fun installHubSkill(body: SkillHubInstallRequest): GatewayResponse<ActionResponse> =
        api.installHubSkill(body)

    override suspend fun previewHubSkill(identifier: String): GatewayResponse<SkillContentResponse> =
        api.previewHubSkill(identifier)

    override suspend fun saveSkillContent(body: SaveSkillContentRequest): GatewayResponse<Unit> =
        api.saveSkillContent(body)

    override suspend fun searchSkillsHub(
        q: String,
        source: String?,
        limit: Int?,
    ): GatewayResponse<SkillHubSearchResponse> = api.searchSkillsHub(q, source, limit)

    override suspend fun toggleSkill(body: ToggleSkillRequest): GatewayResponse<Unit> = api.toggleSkill(body)

    override suspend fun uninstallHubSkill(body: SkillHubUninstallRequest): GatewayResponse<ActionResponse> =
        api.uninstallHubSkill(body)

    override suspend fun updateSkillsFromHub(body: Map<String, String>): GatewayResponse<ActionResponse> =
        api.updateSkillsFromHub(body)
}

class SystemSourceOf(private val api: HermesGatewayApi) : SystemSource {
    override suspend fun addCredentialPoolEntry(body: Map<String, String>): GatewayResponse<JsonObject> =
        api.addCredentialPoolEntry(body)

    override suspend fun checkHermesUpdate(force: Boolean): GatewayResponse<UpdateCheckResponse> =
        api.checkHermesUpdate(force)

    override suspend fun createHook(body: JsonObject): GatewayResponse<JsonObject> = api.createHook(body)

    override suspend fun deleteHook(body: Map<String, String>): GatewayResponse<JsonObject> = api.deleteHook(body)

    override suspend fun downloadBackup(archive: String): GatewayResponse<ByteArray> = api.downloadBackup(archive)

    override suspend fun getActionStatus(
        name: String,
        lines: Int,
    ): GatewayResponse<ActionStatusResponse> = api.getActionStatus(name, lines)

    override suspend fun getCheckpoints(): GatewayResponse<CheckpointsResponse> = api.getCheckpoints()

    override suspend fun getCredentialPool(): GatewayResponse<CredentialPoolResponse> = api.getCredentialPool()

    override suspend fun getCurator(): GatewayResponse<CuratorResponse> = api.getCurator()

    override suspend fun getHooks(): GatewayResponse<HookResponse> = api.getHooks()

    override suspend fun getLearningGraph(profile: String?): GatewayResponse<LearningGraphResponse> =
        api.getLearningGraph(profile)

    override suspend fun getMemory(): GatewayResponse<MemoryResponse> = api.getMemory()

    override suspend fun getPortal(): GatewayResponse<PortalResponse> = api.getPortal()

    override suspend fun getStatus(): GatewayResponse<StatusResponse> = api.getStatus()

    override suspend fun getSystemStats(): GatewayResponse<SystemStatsResponse> = api.getSystemStats()

    override suspend fun getUpdateReceipt(): GatewayResponse<UpdateReceiptResponse> = api.getUpdateReceipt()

    override suspend fun pruneCheckpoints(): GatewayResponse<ActionResponse> = api.pruneCheckpoints()

    override suspend fun removeCredentialPoolEntry(
        provider: String,
        index: Int,
    ): GatewayResponse<JsonObject> = api.removeCredentialPoolEntry(provider, index)

    override suspend fun resetMemory(body: MemoryResetRequest): GatewayResponse<MemoryResetResponse> =
        api.resetMemory(body)

    override suspend fun restartGateway(): GatewayResponse<Unit> = api.restartGateway()

    override suspend fun runConfigMigrate(): GatewayResponse<ActionResponse> = api.runConfigMigrate()

    override suspend fun runCurator(): GatewayResponse<ActionResponse> = api.runCurator()

    override suspend fun runDebugShare(body: JsonObject): GatewayResponse<DebugShareResponse> = api.runDebugShare(body)

    override suspend fun runDoctor(): GatewayResponse<DoctorResponse> = api.runDoctor()

    override suspend fun runDump(): GatewayResponse<ActionResponse> = api.runDump()

    override suspend fun runImport(body: JsonObject): GatewayResponse<ActionResponse> = api.runImport(body)

    override suspend fun runPromptSize(): GatewayResponse<ActionResponse> = api.runPromptSize()

    override suspend fun runSecurityAudit(): GatewayResponse<ActionResponse> = api.runSecurityAudit()

    override suspend fun setCuratorPaused(body: JsonObject): GatewayResponse<JsonObject> = api.setCuratorPaused(body)

    override suspend fun startGateway(): GatewayResponse<Unit> = api.startGateway()

    override suspend fun stopGateway(): GatewayResponse<Unit> = api.stopGateway()

    override suspend fun triggerBackup(): GatewayResponse<ActionResponse> = api.triggerBackup()

    override suspend fun updateHermes(): GatewayResponse<ActionResponse> = api.updateHermes()

    override suspend fun updateSkillsFromHub(body: Map<String, String>): GatewayResponse<ActionResponse> =
        api.updateSkillsFromHub(body)
}

class ToolsetsSourceOf(private val api: HermesGatewayApi) : ToolsetsSource {
    override suspend fun getToolsets(): GatewayResponse<List<Toolset>> = api.getToolsets()

    override suspend fun toggleToolset(
        name: String,
        body: ToolsetToggleRequest,
    ): GatewayResponse<Unit> = api.toggleToolset(name, body)
}

class WebhooksSourceOf(private val api: HermesGatewayApi) : WebhooksSource {
    override suspend fun createWebhook(body: CreateWebhookRequest): GatewayResponse<WebhookSubscription> =
        api.createWebhook(body)

    override suspend fun deleteWebhook(name: String): GatewayResponse<DeleteWebhookResponse> = api.deleteWebhook(name)

    override suspend fun getWebhooks(): GatewayResponse<WebhooksResponse> = api.getWebhooks()

    override suspend fun setWebhookEnabled(
        name: String,
        body: WebhookToggleSubscriptionRequest,
    ): GatewayResponse<Unit> = api.setWebhookEnabled(name, body)

    override suspend fun toggleWebhooks(body: WebhooksToggleRequest): GatewayResponse<Unit> = api.toggleWebhooks(body)
}

class SessionsSourceOf(private val api: HermesGatewayApi) : SessionsSource {
    override suspend fun getSessions(
        limit: Int,
        offset: Int,
        order: String,
        source: String?,
        excludeSources: String?,
        /**
         * Profile whose session store to list. The WS socket is already profile-scoped;
         * this route is not, so omitting it silently lists the **default** profile's
         * sessions for a user working in any other one.
         */
        profile: String?,
    ): GatewayResponse<SessionListResponse> = api.getSessions(limit, offset, order, source, excludeSources)

    override suspend fun getSessionStats(): GatewayResponse<SessionStatsResponse> = api.getSessionStats()

    override suspend fun pruneSessions(body: PruneRequest): GatewayResponse<Unit> = api.pruneSessions(body)
}

class FilesSourceOf(private val api: HermesGatewayApi) : FilesSource {
    override suspend fun listManagedFiles(path: String?): GatewayResponse<ManagedFilesListResponse> =
        api.listManagedFiles(path)

    override suspend fun createManagedDirectory(
        body: ManagedDirectoryCreate,
    ): GatewayResponse<ManagedFileActionResponse> = api.createManagedDirectory(body)

    override suspend fun deleteManagedFile(body: ManagedFileDelete): GatewayResponse<ManagedFileActionResponse> =
        api.deleteManagedFile(body)

    override suspend fun uploadManagedFileStream(
        path: String,
        overwrite: Boolean,
        fileName: String,
        content: ByteArray,
        mimeType: String?,
    ): GatewayResponse<ManagedFileActionResponse> =
        api.uploadManagedFileStream(path, overwrite, fileName, content, mimeType)
}

class LogsSourceOf(private val api: HermesGatewayApi) : LogsSource {
    override suspend fun uploadManagedFileStream(
        path: String,
        overwrite: Boolean,
        fileName: String,
        content: ByteArray,
        mimeType: String?,
    ): GatewayResponse<ManagedFileActionResponse> =
        api.uploadManagedFileStream(path, overwrite, fileName, content, mimeType)

    override suspend fun getLogs(
        file: String,
        lines: Int,
        level: String,
        component: String,
    ): GatewayResponse<LogResponse> = api.getLogs(file, lines, level, component)
}

class AchievementsSourceOf(private val api: HermesGatewayApi) : AchievementsSource {
    override suspend fun getAchievements(): GatewayResponse<AchievementsResponse> = api.getAchievements()

    override suspend fun getRecentUnlocks(): GatewayResponse<List<RecentUnlock>> = api.getRecentUnlocks()

    override suspend fun rescanAchievements(): GatewayResponse<AchievementsResponse> = api.rescanAchievements()

    override suspend fun resetAchievementState(): GatewayResponse<Unit> = api.resetAchievementState()
}
