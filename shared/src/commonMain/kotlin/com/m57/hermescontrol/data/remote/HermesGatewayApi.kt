package com.m57.hermescontrol.data.remote

import com.m57.hermescontrol.data.model.AchievementsResponse
import com.m57.hermescontrol.data.model.ActionResponse
import com.m57.hermescontrol.data.model.ActionStatusResponse
import com.m57.hermescontrol.data.model.ActiveProfileResponse
import com.m57.hermescontrol.data.model.AddMcpServerRequest
import com.m57.hermescontrol.data.model.AgentPluginInstallBody
import com.m57.hermescontrol.data.model.AnalyticsResponse
import com.m57.hermescontrol.data.model.AuxiliaryModelsResponse
import com.m57.hermescontrol.data.model.BulkDeleteRequest
import com.m57.hermescontrol.data.model.BulkDeleteResponse
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
import com.m57.hermescontrol.data.model.ManagedFileRead
import com.m57.hermescontrol.data.model.ManagedFileUpload
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
import com.m57.hermescontrol.data.model.ModelInfoResponse
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
import com.m57.hermescontrol.data.model.ScanStatus
import com.m57.hermescontrol.data.model.SessionInfo
import com.m57.hermescontrol.data.model.SessionLatestDescendantResponse
import com.m57.hermescontrol.data.model.SessionListResponse
import com.m57.hermescontrol.data.model.SessionMessagesResponse
import com.m57.hermescontrol.data.model.SessionRenameRequest
import com.m57.hermescontrol.data.model.SessionSearchResponse
import com.m57.hermescontrol.data.model.SessionStatsResponse
import com.m57.hermescontrol.data.model.SetActiveProfileRequest
import com.m57.hermescontrol.data.model.Skill
import com.m57.hermescontrol.data.model.SkillContentResponse
import com.m57.hermescontrol.data.model.SkillHubInstallRequest
import com.m57.hermescontrol.data.model.SkillHubSearchResponse
import com.m57.hermescontrol.data.model.SkillHubUninstallRequest
import com.m57.hermescontrol.data.model.SkillScanResponse
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
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonObject

/**
 * Gateway endpoints over the Ktor floor, for targets that cannot use Retrofit.
 *
 * This exists to prove the transport migration is viable: the endpoints here hit
 * the same paths, decode with the same [HermesJson], and expose the same result
 * shape as `HermesApiService`, which stays untouched for Android. [GatewayResponse]
 * mirrors Retrofit's `isSuccessful` / `body()` / `errorBody()` deliberately,
 * because that is what `safeApiCall` and the pinned `HermesApiServiceTest` cases
 * are written against — call sites move without changing.
 *
 * Parities that are easy to get wrong, all asserted in [HermesGatewayApiTest]:
 * - request bodies are encoded by [HermesJson], so defaulted fields are omitted:
 *   `BulkDeleteRequest(ids = listOf("session_1"))` encodes to `{"ids":["session_1"]}`.
 * - a malformed body on a 2xx **throws** the bare `SerializationException`, as
 *   Retrofit's converter did (Ktor's wrapping is undone in `decode`).
 * - **session IDs keep their slashes** (issue #468); [ServerBaseUrl.resolve]
 *   concatenates without re-encoding, matching Retrofit's `encoded = true`.
 * - query values are percent-encoded with `%20` for spaces, as OkHttp did, rather
 *   than the `+` some Ktor helpers emit.
 */
@Suppress("TooManyFunctions")
class HermesGatewayApi(
    private val client: HttpClient,
    private val baseUrl: ServerBaseUrl,
) {
    // ── plugins / status ──────────────────────────────────────────────────
    suspend fun getStatus(): GatewayResponse<StatusResponse> = get("api/status")

    suspend fun getSkills(): GatewayResponse<List<Skill>> = get("api/skills")

    // ── sessions ──────────────────────────────────────────────────────────
    suspend fun getSessions(
        limit: Int = 20,
        offset: Int = 0,
        order: String = "recent",
        source: String? = null,
        excludeSources: String? = null,
    ): GatewayResponse<SessionListResponse> =
        get(
            queryPath(
                "api/sessions",
                "limit" to limit.toString(),
                "offset" to offset.toString(),
                "order" to order,
                "source" to source,
                "exclude_sources" to excludeSources,
            ),
        )

    suspend fun getSession(sessionId: String): GatewayResponse<SessionInfo> = get(sessionPath(sessionId))

    suspend fun getSessionLatestDescendant(sessionId: String): GatewayResponse<SessionLatestDescendantResponse> =
        get("${sessionPath(sessionId)}/latest-descendant")

    suspend fun searchSessions(
        q: String,
        profile: String? = null,
        source: String? = null,
        excludeSources: String? = null,
    ): GatewayResponse<SessionSearchResponse> =
        get(
            queryPath(
                "api/sessions/search",
                "q" to q,
                "profile" to profile,
                "source" to source,
                "exclude_sources" to excludeSources,
            ),
        )

    suspend fun getSessionMessages(
        sessionId: String,
        limit: Int? = null,
        offset: Int = 0,
        includeCompacted: Boolean? = null,
        order: String? = null,
    ): GatewayResponse<SessionMessagesResponse> =
        get(
            queryPath(
                "${sessionPath(sessionId)}/messages",
                "limit" to limit?.toString(),
                "offset" to offset.toString(),
                "include_compacted" to includeCompacted?.toString(),
                "order" to order,
            ),
        )

    suspend fun getSessionStats(): GatewayResponse<SessionStatsResponse> = get("api/sessions/stats")

    suspend fun renameSession(
        sessionId: String,
        body: SessionRenameRequest,
    ): GatewayResponse<Unit> =
        empty(
            client.put(url("${sessionPath(sessionId)}/rename")) {
                contentType(ContentType.Application.Json)
                setBody(body)
            },
        )

    suspend fun deleteSession(sessionId: String): GatewayResponse<Unit> =
        empty(client.delete(url(sessionPath(sessionId))))

    suspend fun pruneSessions(body: PruneRequest): GatewayResponse<Unit> =
        empty(
            client.post(url("api/sessions/prune")) {
                contentType(ContentType.Application.Json)
                setBody(body)
            },
        )

    suspend fun bulkDeleteSessions(request: BulkDeleteRequest): GatewayResponse<BulkDeleteResponse> =
        post("api/sessions/bulk-delete", request)

    // ── cron jobs ─────────────────────────────────────────────────────────
    suspend fun getCronJobs(): GatewayResponse<List<CronJob>> = get("api/cron/jobs")

    suspend fun getCronJob(id: String): GatewayResponse<CronJob> = get("api/cron/jobs/$id")

    suspend fun createCronJob(body: CreateCronJobRequest): GatewayResponse<CronJob> = post("api/cron/jobs", body)

    suspend fun updateCronJob(
        id: String,
        body: UpdateCronJobRequest,
    ): GatewayResponse<CronJob> =
        decode(
            client.put(url("api/cron/jobs/$id")) {
                contentType(ContentType.Application.Json)
                setBody(body)
            },
        )

    /**
     * The pause/resume/trigger/delete family answers 200 with an **empty** body,
     * which the pinned cases mock exactly; nothing is parsed.
     */
    suspend fun pauseCronJob(id: String): GatewayResponse<Unit> = empty(client.post(url("api/cron/jobs/$id/pause")))

    suspend fun resumeCronJob(id: String): GatewayResponse<Unit> = empty(client.post(url("api/cron/jobs/$id/resume")))

    suspend fun triggerCronJob(id: String): GatewayResponse<Unit> = empty(client.post(url("api/cron/jobs/$id/trigger")))

    suspend fun deleteCronJob(id: String): GatewayResponse<Unit> = empty(client.delete(url("api/cron/jobs/$id")))

    // ── skills ────────────────────────────────────────────────────────────
    suspend fun getSkillContent(name: String): GatewayResponse<SkillContentResponse> =
        get(queryPath("api/skills/content", "name" to name))

    suspend fun saveSkillContent(body: SaveSkillContentRequest): GatewayResponse<Unit> =
        empty(
            client.put(url("api/skills/content")) {
                contentType(ContentType.Application.Json)
                setBody(body)
            },
        )

    suspend fun searchSkillsHub(
        q: String,
        source: String? = null,
        limit: Int? = null,
    ): GatewayResponse<SkillHubSearchResponse> =
        get(queryPath("api/skills/hub/search", "q" to q, "source" to source, "limit" to limit?.toString()))

    suspend fun previewHubSkill(identifier: String): GatewayResponse<SkillContentResponse> =
        get(queryPath("api/skills/hub/preview", "identifier" to identifier))

    suspend fun scanHubSkill(identifier: String): GatewayResponse<SkillScanResponse> =
        get(queryPath("api/skills/hub/scan", "identifier" to identifier))

    suspend fun getHubSources(): GatewayResponse<List<String>> = get("api/skills/hub/sources")

    suspend fun installHubSkill(body: SkillHubInstallRequest): GatewayResponse<ActionResponse> =
        post("api/skills/hub/install", body)

    suspend fun uninstallHubSkill(body: SkillHubUninstallRequest): GatewayResponse<ActionResponse> =
        post("api/skills/hub/uninstall", body)

    suspend fun toggleSkill(body: ToggleSkillRequest): GatewayResponse<Unit> =
        empty(
            client.put(url("api/skills/toggle")) {
                contentType(ContentType.Application.Json)
                setBody(body)
            },
        )

    // ── plugins: hermes-achievements ──────────────────────────────────────
    suspend fun getAchievements(): GatewayResponse<AchievementsResponse> =
        get("api/plugins/hermes-achievements/achievements")

    suspend fun getAchievementScanStatus(): GatewayResponse<ScanStatus> =
        get("api/plugins/hermes-achievements/scan-status")

    suspend fun rescanAchievements(): GatewayResponse<AchievementsResponse> =
        decode(client.post(url("api/plugins/hermes-achievements/rescan")))

    suspend fun resetAchievementState(): GatewayResponse<Unit> =
        empty(client.post(url("api/plugins/hermes-achievements/reset-state")))

    suspend fun getRecentUnlocks(): GatewayResponse<List<RecentUnlock>> =
        get("api/plugins/hermes-achievements/recent-unlocks")

    /** Pinned by testRescanPlugins: a GET that answers with an empty body. */
    suspend fun rescanPlugins(): GatewayResponse<Unit> = empty(client.get(url("api/dashboard/plugins/rescan")))

    // ── plugins: kanban ───────────────────────────────────────────────────
    suspend fun getKanbanBoards(): GatewayResponse<KanbanBoardsResponse> = get("api/plugins/kanban/boards")

    suspend fun getKanbanBoard(): GatewayResponse<KanbanBoardResponse> = get("api/plugins/kanban/board")

    suspend fun switchKanbanBoard(slug: String): GatewayResponse<Unit> =
        empty(client.post(url("api/plugins/kanban/boards/$slug/switch")))

    suspend fun updateKanbanTask(
        taskId: String,
        body: Map<String, String?>,
    ): GatewayResponse<Unit> =
        empty(
            client.patch(url("api/plugins/kanban/tasks/$taskId")) {
                contentType(ContentType.Application.Json)
                setBody(body)
            },
        )

    suspend fun createKanbanTask(
        board: String?,
        task: CreateTaskBody,
    ): GatewayResponse<KanbanTask> =
        decode(
            client.post(url(queryPath("api/plugins/kanban/tasks", "board" to board))) {
                contentType(ContentType.Application.Json)
                setBody(task)
            },
        )

    // ── mcp servers ───────────────────────────────────────────────────────
    suspend fun getMcpServers(): GatewayResponse<McpServersResponse> = get("api/mcp/servers")

    suspend fun toggleMcpServer(
        name: String,
        body: McpServerToggleRequest,
    ): GatewayResponse<Unit> =
        empty(
            client.put(url("api/mcp/servers/$name/enabled")) {
                contentType(ContentType.Application.Json)
                setBody(body)
            },
        )

    suspend fun testMcpServer(name: String): GatewayResponse<McpServerTestResponse> =
        decode(client.post(url("api/mcp/servers/$name/test")))

    suspend fun deleteMcpServer(name: String): GatewayResponse<Unit> =
        empty(client.delete(url("api/mcp/servers/$name")))

    suspend fun addMcpServer(body: AddMcpServerRequest): GatewayResponse<McpServer> = post("api/mcp/servers", body)

    /**
     * The Retrofit signature was `Map<String, Any>`, which kotlinx-serialization
     * cannot encode at all (no serializer for `Any`) — it only ever worked
     * because no test exercised it. `JsonObject` carries the same payload and
     * actually serialises.
     */
    suspend fun updateMcpServer(
        name: String,
        body: JsonObject,
    ): GatewayResponse<McpServer> =
        decode(
            client.put(url("api/mcp/servers/$name")) {
                contentType(ContentType.Application.Json)
                setBody(body)
            },
        )

    suspend fun authMcpServer(name: String): GatewayResponse<McpOAuthFlowResponse> =
        decode(client.post(url("api/mcp/servers/$name/auth")))

    suspend fun getMcpOAuthFlowStatus(flowId: String): GatewayResponse<McpOAuthFlowResponse> =
        get("api/mcp/oauth/flows/$flowId")

    suspend fun getMcpCatalog(): GatewayResponse<McpCatalogResponse> = get("api/mcp/catalog")

    /** Same `Any` problem as [updateMcpServer]; returned as a JSON object instead. */
    suspend fun installMcpCatalogEntry(body: McpCatalogInstallRequest): GatewayResponse<JsonObject> =
        decode(
            client.post(url("api/mcp/catalog/install")) {
                contentType(ContentType.Application.Json)
                setBody(body)
            },
        )

    // ── ops ───────────────────────────────────────────────────────────────
    suspend fun triggerBackup(): GatewayResponse<ActionResponse> = postAndDecode("api/ops/backup")

    suspend fun runDoctor(): GatewayResponse<DoctorResponse> = postAndDecode("api/ops/doctor")

    suspend fun runSecurityAudit(): GatewayResponse<ActionResponse> = postAndDecode("api/ops/security-audit")

    suspend fun runPromptSize(): GatewayResponse<ActionResponse> = postAndDecode("api/ops/prompt-size")

    suspend fun runDump(): GatewayResponse<ActionResponse> = postAndDecode("api/ops/dump")

    suspend fun runConfigMigrate(): GatewayResponse<ActionResponse> = postAndDecode("api/ops/config-migrate")

    /** The Retrofit default was `emptyMap()`, so the wire body is `{}`. */
    suspend fun updateSkillsFromHub(body: Map<String, String> = emptyMap()): GatewayResponse<ActionResponse> =
        post("api/skills/hub/update", body)

    /**
     * Returns the archive as raw bytes. The Retrofit signature was
     * `Response<ResponseBody>` and nothing decoded it as JSON — a download must
     * pass through untouched, which is asserted with a deliberately non-JSON
     * payload. This buffers the whole archive in memory; a console client
     * streaming multi-hundred-MB backups should switch to `bodyAsChannel()`.
     */
    suspend fun downloadBackup(archive: String): GatewayResponse<ByteArray> {
        val response = client.get(url(queryPath("api/ops/backup/download", "archive" to archive)))
        return if (response.status.isSuccess()) {
            GatewayResponse(response.status.value, response.bodyAsBytes(), null)
        } else {
            GatewayResponse(response.status.value, null, response.bodyAsText())
        }
    }

    /** `Map<String, Any>` in Retrofit; unusable with kotlinx, so `JsonObject`. */
    suspend fun runImport(body: JsonObject): GatewayResponse<ActionResponse> = post("api/ops/import", body)

    suspend fun runDebugShare(body: JsonObject): GatewayResponse<DebugShareResponse> = post("api/ops/debug-share", body)

    suspend fun getCheckpoints(): GatewayResponse<CheckpointsResponse> = get("api/ops/checkpoints")

    suspend fun pruneCheckpoints(): GatewayResponse<ActionResponse> = postAndDecode("api/ops/checkpoints/prune")

    suspend fun getHooks(): GatewayResponse<HookResponse> = get("api/ops/hooks")

    suspend fun createHook(body: JsonObject): GatewayResponse<JsonObject> = post("api/ops/hooks", body)

    /** A DELETE that carries a JSON body — Retrofit allowed it; so does Ktor. */
    suspend fun deleteHook(body: Map<String, String>): GatewayResponse<JsonObject> =
        decode(
            client.delete(url("api/ops/hooks")) {
                contentType(ContentType.Application.Json)
                setBody(body)
            },
        )

    // ── system / analytics ────────────────────────────────────────────────
    suspend fun getSystemStats(): GatewayResponse<SystemStatsResponse> = get("api/system/stats")

    suspend fun getAnalytics(
        days: Int,
        profile: String? = null,
    ): GatewayResponse<AnalyticsResponse> =
        get(queryPath("api/analytics/usage", "days" to days.toString(), "profile" to profile))

    suspend fun getModelsAnalytics(
        days: Int,
        profile: String? = null,
    ): GatewayResponse<ModelsAnalyticsResponse> =
        get(queryPath("api/analytics/models", "days" to days.toString(), "profile" to profile))

    // ── gateway control ───────────────────────────────────────────────────
    suspend fun startGateway(): GatewayResponse<Unit> = empty(client.post(url("api/gateway/start")))

    suspend fun stopGateway(): GatewayResponse<Unit> = empty(client.post(url("api/gateway/stop")))

    suspend fun restartGateway(): GatewayResponse<Unit> = empty(client.post(url("api/gateway/restart")))

    // ── profiles ───────────────────────────────────────────────────────────
    suspend fun getProfiles(): GatewayResponse<ProfilesResponse> = get("api/profiles")

    suspend fun createProfile(body: CreateProfileRequest): GatewayResponse<Unit> =
        empty(
            client.post(url("api/profiles")) {
                contentType(ContentType.Application.Json)
                setBody(body)
            },
        )

    suspend fun getActiveProfile(): GatewayResponse<ActiveProfileResponse> = get("api/profiles/active")

    suspend fun setActiveProfile(body: SetActiveProfileRequest): GatewayResponse<Unit> =
        empty(
            client.post(url("api/profiles/active")) {
                contentType(ContentType.Application.Json)
                setBody(body)
            },
        )

    suspend fun getProfileSoul(name: String): GatewayResponse<ProfileSoulResponse> = get("api/profiles/$name/soul")

    suspend fun updateProfileSoul(
        name: String,
        body: UpdateProfileSoulRequest,
    ): GatewayResponse<Unit> =
        empty(
            client.put(url("api/profiles/$name/soul")) {
                contentType(ContentType.Application.Json)
                setBody(body)
            },
        )

    suspend fun updateProfileModel(
        name: String,
        body: UpdateProfileModelRequest,
    ): GatewayResponse<Unit> =
        empty(
            client.put(url("api/profiles/$name/model")) {
                contentType(ContentType.Application.Json)
                setBody(body)
            },
        )

    suspend fun updateProfileDescription(
        name: String,
        body: UpdateProfileDescriptionRequest,
    ): GatewayResponse<Unit> =
        empty(
            client.put(url("api/profiles/$name/description")) {
                contentType(ContentType.Application.Json)
                setBody(body)
            },
        )

    // ── tools ─────────────────────────────────────────────────────────────
    suspend fun getToolsets(): GatewayResponse<List<Toolset>> = get("api/tools/toolsets")

    suspend fun toggleToolset(
        name: String,
        body: ToolsetToggleRequest,
    ): GatewayResponse<Unit> =
        empty(
            client.put(url("api/tools/toolsets/$name")) {
                contentType(ContentType.Application.Json)
                setBody(body)
            },
        )

    // ── config ────────────────────────────────────────────────────────────
    suspend fun getRawConfig(): GatewayResponse<RawConfigResponse> = get("api/config/raw")

    suspend fun updateRawConfig(body: UpdateRawConfigRequest): GatewayResponse<Unit> =
        empty(
            client.put(url("api/config/raw")) {
                contentType(ContentType.Application.Json)
                setBody(body)
            },
        )

    /**
     * `Map<String, JsonElement>` in Retrofit: kotlinx-serialization can decode it
     * but it cannot encode the write side. This endpoint is GET-only, so the
     * return type is enough — no JsonObject dance is required here.
     */
    suspend fun getConfig(): GatewayResponse<Map<String, kotlinx.serialization.json.JsonElement>> = get("api/config")

    suspend fun getConfigSchema(): GatewayResponse<ConfigSchemaResponse> = get("api/config/schema")

    suspend fun getConfigDefaults(): GatewayResponse<Map<String, kotlinx.serialization.json.JsonElement>> =
        get("api/config/defaults")

    suspend fun updateConfig(body: ConfigUpdateRequest): GatewayResponse<Unit> =
        empty(
            client.put(url("api/config")) {
                contentType(ContentType.Application.Json)
                setBody(body)
            },
        )

    // ── webhooks ──────────────────────────────────────────────────────────
    suspend fun getWebhooks(): GatewayResponse<WebhooksResponse> = get("api/webhooks")

    suspend fun toggleWebhooks(body: WebhooksToggleRequest): GatewayResponse<Unit> =
        empty(
            client.post(url("api/webhooks/enable")) {
                contentType(ContentType.Application.Json)
                setBody(body)
            },
        )

    suspend fun createWebhook(body: CreateWebhookRequest): GatewayResponse<WebhookSubscription> =
        post("api/webhooks", body)

    suspend fun deleteWebhook(name: String): GatewayResponse<DeleteWebhookResponse> =
        decode(client.delete(url("api/webhooks/$name")))

    suspend fun setWebhookEnabled(
        name: String,
        body: WebhookToggleSubscriptionRequest,
    ): GatewayResponse<Unit> =
        empty(
            client.put(url("api/webhooks/$name/enabled")) {
                contentType(ContentType.Application.Json)
                setBody(body)
            },
        )

    // ── model ─────────────────────────────────────────────────────────────
    suspend fun getModelInfo(): GatewayResponse<ModelInfoResponse> = get("api/model/info")

    suspend fun getModelOptions(
        refresh: Boolean = false,
        includeUnconfigured: Boolean = true,
    ): GatewayResponse<ModelOptionsResponse> =
        get(
            queryPath(
                "api/model/options",
                "refresh" to refresh.toString(),
                "include_unconfigured" to includeUnconfigured.toString(),
            ),
        )

    suspend fun getAuxiliaryModels(): GatewayResponse<AuxiliaryModelsResponse> = get("api/model/auxiliary")

    suspend fun getMoaModels(): GatewayResponse<MoaConfigResponse> = get("api/model/moa")

    suspend fun saveMoaModels(body: MoaConfigResponse): GatewayResponse<MoaConfigResponse> =
        decode(
            client.put(url("api/model/moa")) {
                contentType(ContentType.Application.Json)
                setBody(body)
            },
        )

    suspend fun setModelAssignment(body: ModelAssignmentRequest): GatewayResponse<ModelAssignmentResponse> =
        post("api/model/set", body)

    // ── logs ──────────────────────────────────────────────────────────────
    suspend fun getLogs(
        file: String = "agent",
        lines: Int = 100,
        level: String = "ALL",
        component: String = "all",
    ): GatewayResponse<LogResponse> =
        get(
            queryPath(
                "api/logs",
                "file" to file,
                "lines" to lines.toString(),
                "level" to level,
                "component" to component,
            ),
        )

    // ── dashboard plugins ─────────────────────────────────────────────────
    suspend fun getPlugins(): GatewayResponse<PluginsHubResponse> = get("api/dashboard/plugins/hub")

    suspend fun installPlugin(body: AgentPluginInstallBody): GatewayResponse<Unit> =
        empty(
            client.post(url("api/dashboard/agent-plugins/install")) {
                contentType(ContentType.Application.Json)
                setBody(body)
            },
        )

    /** Plugin names preserve slashes (`owner/repo`); issue #468. */
    suspend fun uninstallPlugin(name: String): GatewayResponse<Unit> =
        empty(client.delete(url("api/dashboard/agent-plugins/$name")))

    suspend fun updatePlugin(name: String): GatewayResponse<Unit> =
        empty(client.post(url("api/dashboard/agent-plugins/$name/update")))

    suspend fun enablePlugin(name: String): GatewayResponse<Unit> =
        empty(client.post(url("api/dashboard/agent-plugins/$name/enable")))

    suspend fun disablePlugin(name: String): GatewayResponse<Unit> =
        empty(client.post(url("api/dashboard/agent-plugins/$name/disable")))

    suspend fun savePluginProviders(body: PluginProvidersPutRequest): GatewayResponse<Unit> =
        empty(
            client.put(url("api/dashboard/plugin-providers")) {
                contentType(ContentType.Application.Json)
                setBody(body)
            },
        )

    suspend fun setPluginVisibility(
        name: String,
        body: Map<String, Boolean>,
    ): GatewayResponse<Unit> =
        empty(
            client.post(url("api/dashboard/plugins/$name/visibility")) {
                contentType(ContentType.Application.Json)
                setBody(body)
            },
        )

    // ── messaging platforms ───────────────────────────────────────────────
    suspend fun getMessagingPlatforms(profile: String? = null): GatewayResponse<MessagingPlatformResponse> =
        get(queryPath("api/messaging/platforms", "profile" to profile))

    suspend fun configurePlatform(
        platformId: String,
        config: MessagingPlatformUpdate,
    ): GatewayResponse<Unit> =
        empty(
            client.put(url("api/messaging/platforms/$platformId")) {
                contentType(ContentType.Application.Json)
                setBody(config)
            },
        )

    suspend fun testMessagingPlatform(platformId: String): GatewayResponse<MessagingPlatformTestResult> =
        decode(client.post(url("api/messaging/platforms/$platformId/test")))

    suspend fun removeMessagingPlatform(platformId: String): GatewayResponse<Unit> =
        empty(client.delete(url("api/messaging/platforms/$platformId")))

    suspend fun startTelegramOnboarding(
        body: TelegramOnboardingStartRequest,
    ): GatewayResponse<TelegramOnboardingStartResponse> = post("api/messaging/telegram/onboarding/start", body)

    suspend fun getTelegramOnboardingStatus(pairingId: String): GatewayResponse<TelegramOnboardingStatusResponse> =
        get("api/messaging/telegram/onboarding/$pairingId")

    suspend fun applyTelegramOnboarding(
        pairingId: String,
        body: TelegramOnboardingApplyRequest,
    ): GatewayResponse<TelegramOnboardingApplyResponse> =
        post("api/messaging/telegram/onboarding/$pairingId/apply", body)

    /** Retrofit used `@HTTP(method = "DELETE", hasBody = false)` — a DELETE with no body. */
    suspend fun cancelTelegramOnboarding(pairingId: String): GatewayResponse<Unit> =
        empty(client.delete(url("api/messaging/telegram/onboarding/$pairingId")))

    // ── OAuth providers (issue #534) ──────────────────────────────────────
    suspend fun getOAuthProviders(): GatewayResponse<OAuthProvidersResponse> = get("api/providers/oauth")

    suspend fun disconnectOAuthProvider(providerId: String): GatewayResponse<Unit> =
        empty(client.delete(url("api/providers/oauth/$providerId")))

    suspend fun startOAuthLogin(providerId: String): GatewayResponse<OAuthStartResponse> =
        decode(client.post(url("api/providers/oauth/$providerId/start")))

    suspend fun submitOAuthCode(
        providerId: String,
        body: OAuthSubmitRequest,
    ): GatewayResponse<OAuthSubmitResponse> = post("api/providers/oauth/$providerId/submit", body)

    suspend fun pollOAuthSession(
        providerId: String,
        sessionId: String,
    ): GatewayResponse<OAuthPollResponse> = get("api/providers/oauth/$providerId/poll/$sessionId")

    /** Different path: `oauth/sessions/{session_id}`, not `oauth/{provider}/...`. */
    suspend fun cancelOAuthSession(sessionId: String): GatewayResponse<Unit> =
        empty(client.delete(url("api/providers/oauth/sessions/$sessionId")))

    // ── env vars ──────────────────────────────────────────────────────────
    suspend fun getEnvVars(): GatewayResponse<Map<String, EnvVarConfig>> = get("api/env")

    suspend fun updateEnvVar(body: EnvVarUpdate): GatewayResponse<Unit> =
        empty(
            client.put(url("api/env")) {
                contentType(ContentType.Application.Json)
                setBody(body)
            },
        )

    suspend fun revealEnvVar(request: EnvVarRevealRequest): GatewayResponse<EnvVarRevealResponse> =
        post("api/env/reveal", request)

    /** Retrofit `@HTTP(method = "DELETE", hasBody = true)` — a DELETE carrying a JSON body. */
    suspend fun deleteEnvVar(request: EnvVarDeleteRequest): GatewayResponse<Unit> =
        empty(
            client.delete(url("api/env")) {
                contentType(ContentType.Application.Json)
                setBody(request)
            },
        )

    // ── admin: Hermes update ──────────────────────────────────────────────
    suspend fun checkHermesUpdate(force: Boolean = false): GatewayResponse<UpdateCheckResponse> =
        get(queryPath("api/hermes/update/check", "force" to force.toString()))

    suspend fun updateHermes(): GatewayResponse<ActionResponse> = postAndDecode("api/hermes/update")

    suspend fun getUpdateReceipt(): GatewayResponse<UpdateReceiptResponse> = get("api/hermes/update/receipt")

    // ── admin: portal / learning ──────────────────────────────────────────
    suspend fun getPortal(): GatewayResponse<PortalResponse> = get("api/portal")

    suspend fun getLearningGraph(profile: String? = null): GatewayResponse<LearningGraphResponse> =
        get(queryPath("api/learning/graph", "profile" to profile))

    // ── admin: curator ────────────────────────────────────────────────────
    suspend fun getCurator(): GatewayResponse<CuratorResponse> = get("api/curator")

    /** `Map<String, Any>` in Retrofit; using `JsonObject` to make it actually serialise. */
    suspend fun setCuratorPaused(body: JsonObject): GatewayResponse<JsonObject> =
        decode(
            client.put(url("api/curator/paused")) {
                contentType(ContentType.Application.Json)
                setBody(body)
            },
        )

    suspend fun runCurator(): GatewayResponse<ActionResponse> = postAndDecode("api/curator/run")

    // ── admin: memory ─────────────────────────────────────────────────────
    suspend fun getMemory(): GatewayResponse<MemoryResponse> = get("api/memory")

    suspend fun resetMemory(body: MemoryResetRequest): GatewayResponse<MemoryResetResponse> =
        post("api/memory/reset", body)

    // ── admin: credential pool ────────────────────────────────────────────
    suspend fun getCredentialPool(): GatewayResponse<CredentialPoolResponse> = get("api/credentials/pool")

    /** Retrofit `@Body body: Map<String, String>`; kotlinx encodes a `Map<String, String>` fine. */
    suspend fun addCredentialPoolEntry(body: Map<String, String>): GatewayResponse<JsonObject> =
        post("api/credentials/pool", body)

    suspend fun removeCredentialPoolEntry(
        provider: String,
        index: Int,
    ): GatewayResponse<JsonObject> = decode(client.delete(url("api/credentials/pool/$provider/$index")))

    // ── admin: action status (log viewer) ─────────────────────────────────
    suspend fun getActionStatus(
        name: String,
        lines: Int = 200,
    ): GatewayResponse<ActionStatusResponse> = get(queryPath("api/actions/$name/status", "lines" to lines.toString()))

    // ── managed files ─────────────────────────────────────────────────────
    suspend fun listManagedFiles(path: String? = null): GatewayResponse<ManagedFilesListResponse> =
        get(queryPath("api/files", "path" to path))

    suspend fun readManagedFile(path: String): GatewayResponse<ManagedFileRead> =
        get(queryPath("api/files/read", "path" to path))

    /**
     * Streaming download. Like [downloadBackup] this returns raw bytes — the
     * backend may send `application/octet-stream`, and the file might be large
     * enough that parsing it as JSON would corrupt the response. The whole file
     * is buffered in memory; console clients should switch to `bodyAsChannel()`
     * for multi-hundred-MB media.
     */
    suspend fun downloadManagedFile(path: String): GatewayResponse<ByteArray> {
        val response = client.get(url(queryPath("api/files/download", "path" to path)))
        return if (response.status.isSuccess()) {
            GatewayResponse(response.status.value, response.bodyAsBytes(), null)
        } else {
            GatewayResponse(response.status.value, null, response.bodyAsText())
        }
    }

    suspend fun uploadManagedFile(body: ManagedFileUpload): GatewayResponse<ManagedFileActionResponse> =
        post("api/files/upload", body)

    /**
     * Multipart upload, mirroring `@Multipart` with `@Part("path")`,
     * `@Part("overwrite")` and the file part.
     *
     * The Retrofit types were JVM-only (`okhttp3.RequestBody`,
     * `okhttp3.MultipartBody.Part`), so the multiplatform port takes the bytes
     * directly. That buffers the whole file; a streaming variant would pass an
     * `InputProvider` over a channel instead. `io.ktor.client.request.forms`
     * ships inside ktor-client-core, so no extra dependency is involved.
     */
    suspend fun uploadManagedFileStream(
        path: String,
        overwrite: Boolean,
        fileName: String,
        content: ByteArray,
        mimeType: String? = null,
    ): GatewayResponse<ManagedFileActionResponse> =
        decode(
            client.post(url("api/files/upload-stream")) {
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append("path", path)
                            append("overwrite", overwrite.toString())
                            append(
                                "file",
                                content,
                                Headers.build {
                                    append(
                                        HttpHeaders.ContentType,
                                        mimeType ?: ContentType.Application.OctetStream.toString(),
                                    )
                                    append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                                },
                            )
                        },
                    ),
                )
            },
        )

    suspend fun createManagedDirectory(body: ManagedDirectoryCreate): GatewayResponse<ManagedFileActionResponse> =
        post("api/files/mkdir", body)

    /** Retrofit `@HTTP(method = "DELETE", hasBody = true)`. */
    suspend fun deleteManagedFile(body: ManagedFileDelete): GatewayResponse<ManagedFileActionResponse> =
        decode(
            client.delete(url("api/files")) {
                contentType(ContentType.Application.Json)
                setBody(body)
            },
        )

    // ── internals ─

    /**
     * Session IDs are generated with `/` in them (issue #468). [resolve] appends
     * the path without re-encoding, so those slashes survive — the same thing
     * Retrofit's `@Path(encoded = true)` did.
     */
    private fun sessionPath(sessionId: String): String = "api/sessions/$sessionId"

    private fun url(path: String): String = baseUrl.resolve(path).toString()

    private suspend inline fun <reified T> get(path: String): GatewayResponse<T> = decode(client.get(url(path)))

    private suspend inline fun <reified B, reified T> post(
        path: String,
        body: B,
    ): GatewayResponse<T> =
        decode(
            client.post(url(path)) {
                contentType(ContentType.Application.Json)
                setBody(body)
            },
        )

    /**
     * Status codes become values; deserialization failures propagate.
     *
     * The body is decoded from raw text with [HermesJson] rather than through
     * `ContentNegotiation`. Ktor's converter only engages when the response
     * carries a matching `Content-Type`, and throws [NoTransformationFoundException]
     * when it does not; Retrofit's converter never inspected the response
     * content type at all. Decoding the text directly keeps the two transports'
     * behaviour identical and restores the bare [SerializationException] that
     * the app's existing `catch (SerializationException)` sites expect.
     */
    private suspend inline fun <reified T> decode(response: HttpResponse): GatewayResponse<T> {
        if (!response.status.isSuccess()) {
            return GatewayResponse(response.status.value, null, response.bodyAsText())
        }
        val text = response.bodyAsText()
        if (text.isBlank()) {
            // No content (204/205, or an intentionally empty 200) is a successful
            // response with no value, not a decode failure. Retrofit behaved the
            // same way by leaving `body()` null.
            return GatewayResponse(response.status.value, null, null)
        }
        return GatewayResponse(response.status.value, HermesJson.instance.decodeFromString<T>(text), null)
    }

    /** For endpoints typed `Response<Unit>`, where the body is not read. */
    private suspend fun empty(response: HttpResponse): GatewayResponse<Unit> =
        if (response.status.isSuccess()) {
            GatewayResponse(response.status.value, Unit, null)
        } else {
            GatewayResponse(response.status.value, null, response.bodyAsText())
        }

    /** For POSTs with no request body that still decode a response. */
    private suspend inline fun <reified T> postAndDecode(path: String): GatewayResponse<T> =
        decode(client.post(url(path)))

    private fun queryPath(
        path: String,
        vararg params: Pair<String, String?>,
    ): String {
        val query =
            params.mapNotNull { (name, value) -> value?.let { "$name=${encodeQueryValue(it)}" } }
        return if (query.isEmpty()) path else "$path?" + query.joinToString("&")
    }

    /**
     * Percent-encode a query value the way OkHttp did: space becomes `%20`, not
     * `+`. Kept local so the encoding cannot drift with a Ktor helper's defaults.
     */
    private fun encodeQueryValue(value: String): String {
        val sb = StringBuilder(value.length)
        for (ch in value) {
            val unreserved = (ch.isLetterOrDigit() && ch.code < 128) || ch in "-._~"
            if (unreserved) {
                sb.append(ch)
            } else {
                for (byte in ch.toString().encodeToByteArray()) {
                    val b = byte.toInt() and 0xFF
                    sb.append('%').append(HEX_DIGITS[b shr 4]).append(HEX_DIGITS[b and 0xF])
                }
            }
        }
        return sb.toString()
    }

    private companion object {
        const val HEX_DIGITS = "0123456789ABCDEF"
    }
}

/**
 * Ktor-side counterpart of `retrofit2.Response<T>`, shaped to match so migration
 * of call sites is mechanical rather than a rewrite.
 */
class GatewayResponse<out T>(
    val code: Int,
    private val value: T?,
    private val failureText: String?,
) {
    val isSuccessful: Boolean get() = code in 200..299

    fun body(): T? = value

    fun errorBody(): String? = failureText

    companion object {
        const val OK: Int = 200

        /** A successful response carrying [value]. */
        fun <T> success(value: T): GatewayResponse<T> = GatewayResponse(OK, value, null)

        /** A failed response carrying no value and an optional error body. */
        fun <T> error(
            code: Int,
            failureText: String? = null,
        ): GatewayResponse<T> = GatewayResponse(code, null, failureText)
    }
}
