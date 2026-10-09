package com.m57.hermescontrol.data.remote

import com.m57.hermescontrol.data.model.AgentPluginInstallBody
import com.m57.hermescontrol.data.model.BulkDeleteRequest
import com.m57.hermescontrol.data.model.ConfigUpdateRequest
import com.m57.hermescontrol.data.model.CreateProfileRequest
import com.m57.hermescontrol.data.model.CreateTaskBody
import com.m57.hermescontrol.data.model.CreateWebhookRequest
import com.m57.hermescontrol.data.model.EnvVarDeleteRequest
import com.m57.hermescontrol.data.model.EnvVarRevealRequest
import com.m57.hermescontrol.data.model.EnvVarUpdate
import com.m57.hermescontrol.data.model.McpCatalogInstallRequest
import com.m57.hermescontrol.data.model.McpServerToggleRequest
import com.m57.hermescontrol.data.model.MemoryResetRequest
import com.m57.hermescontrol.data.model.MessagingPlatformUpdate
import com.m57.hermescontrol.data.model.PluginProvidersPutRequest
import com.m57.hermescontrol.data.model.SessionRenameRequest
import com.m57.hermescontrol.data.model.SetActiveProfileRequest
import com.m57.hermescontrol.data.model.TelegramOnboardingApplyRequest
import com.m57.hermescontrol.data.model.TelegramOnboardingStartRequest
import com.m57.hermescontrol.data.model.ToggleSkillRequest
import com.m57.hermescontrol.data.model.ToolsetToggleRequest
import com.m57.hermescontrol.data.model.UpdateProfileDescriptionRequest
import com.m57.hermescontrol.data.model.UpdateProfileModelRequest
import com.m57.hermescontrol.data.model.UpdateProfileSoulRequest
import com.m57.hermescontrol.data.model.UpdateRawConfigRequest
import com.m57.hermescontrol.data.model.WebhookToggleSubscriptionRequest
import com.m57.hermescontrol.data.model.WebhooksToggleRequest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Parity spec for [HermesGatewayApi] against the frozen Retrofit contract in
 * `HermesApiServiceTest` (which is never edited): same paths, same method, same
 * encoded body, same exception on malformed payloads.
 */
class HermesGatewayApiTest {
    private val baseUrl = ServerEndpoint.parse("https://gw.example/").baseUrl

    private fun api(engine: MockEngine) = HermesGatewayApi(createHermesHttpClient(engine), baseUrl)

    private fun jsonResponder(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
        onRequest: (pathAndQuery: String, method: String, body: String?) -> Unit = { _, _, _ -> },
    ) = MockEngine { request ->
        val text = (request.body as? TextContent)?.text
        val query = request.url.encodedQuery
        val pathAndQuery =
            if (query.isEmpty()) request.url.encodedPath else request.url.encodedPath + "?" + query
        onRequest(pathAndQuery, request.method.value, text)
        respond(
            body,
            status,
            headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )
    }

    @Test
    fun getStatus_requestsApiStatusAndDecodesSnakeCase() =
        runTest {
            var path: String? = null
            val engine =
                jsonResponder("""{"version":"1.2.3","gateway_running":true,"active_sessions":2}""") { p, _, _ ->
                    path = p
                }

            val response = api(engine).getStatus()

            assertEquals("/api/status", path)
            assertTrue(response.isSuccessful)
            assertEquals("1.2.3", response.body()?.version)
            assertEquals(true, response.body()?.gateway_running)
            assertEquals(2, response.body()?.active_sessions)
        }

    @Test
    fun getSkills_requestsApiSkillsByGetAndDecodesList() =
        runTest {
            var path: String? = null
            var method: String? = null
            val engine =
                jsonResponder(
                    """[{"name":"weather","description":"info","category":"utils","enabled":true}]""",
                ) { p, m, _ ->
                    path = p
                    method = m
                }

            val response = api(engine).getSkills()

            assertEquals("/api/skills", path)
            assertEquals("GET", method)
            assertEquals(1, response.body()?.size)
            assertEquals("weather", response.body()?.get(0)?.name)
        }

    @Test
    fun bulkDelete_postsSnakeCaseBodyWithDefaultsOmitted() =
        runTest {
            var path: String? = null
            var method: String? = null
            var sent: String? = null
            val engine =
                jsonResponder("""{"ok":true,"deleted":1}""") { p, m, b ->
                    path = p
                    method = m
                    sent = b
                }

            val response =
                api(engine).bulkDeleteSessions(BulkDeleteRequest(ids = listOf("session_1"), delete_all = false))

            assertEquals("/api/sessions/bulk-delete", path)
            assertEquals("POST", method)
            assertEquals("""{"ids":["session_1"]}""", sent)
            assertEquals(1, response.body()?.deleted)
        }

    @Test
    fun serverErrorsBecomeValuesNotThrows() =
        runTest {
            val engine = jsonResponder("""{"detail":"nope"}""", HttpStatusCode.Unauthorized)

            val response = api(engine).getStatus()

            assertFalse(response.isSuccessful)
            assertEquals(401, response.code)
            assertNull(response.body())
            assertNotNull(response.errorBody())
        }

    @Test
    fun malformedJsonOnSuccess_throwsSerializationExceptionLikeRetrofit() =
        runTest {
            val engine = jsonResponder("""[{"name":"weather"""")

            // Retrofits's converter threw the bare type; Ktor wraps it in
            // JsonConvertException, which decode() unwraps back to this.
            assertNotNull(
                runCatching { api(engine).getSkills() }.exceptionOrNull() as? SerializationException,
            )
        }

    @Test
    fun getSessions_sendsTheSameDefaultQueryAsRetrofit() =
        runTest {
            var pathAndQuery: String? = null
            val engine =
                jsonResponder("""{"sessions":[{"id":"session_1","title":"t"}],"total":1}""") { p, _, _ ->
                    pathAndQuery = p
                }

            val response = api(engine).getSessions()

            assertEquals("/api/sessions?limit=20&offset=0&order=recent", pathAndQuery)
            assertEquals(1, response.body()?.total)
            assertEquals("session_1", response.body()?.sessions?.first()?.id)
        }

    @Test
    fun getSessions_missingSessionsField_throwsSerializationException() =
        runTest {
            val engine = jsonResponder("{}")

            assertNotNull(
                runCatching { api(engine).getSessions() }.exceptionOrNull() as? SerializationException,
            )
        }

    @Test
    fun getSessionMessages_preservesSlashesInSessionId() =
        runTest {
            var pathAndQuery: String? = null
            val engine =
                jsonResponder("""{"messages":[]}""") { p, _, _ ->
                    pathAndQuery = p
                }

            api(engine).getSessionMessages("agent/2026/10/foo")

            // issue #468: the backend generates IDs containing '/'. Retrofit passed
            // them through with `encoded = true`; they must not become %2F.
            assertEquals("/api/sessions/agent/2026/10/foo/messages?offset=0", pathAndQuery)
        }

    @Test
    fun getSessionMessages_malformedJson_throwsSerializationException() =
        runTest {
            val engine = jsonResponder("""{"messages": [""")

            assertNotNull(
                runCatching { api(engine).getSessionMessages("session_1") }
                    .exceptionOrNull() as? SerializationException,
            )
        }

    @Test
    fun getSessionMessages_missingMessagesField_throwsSerializationException() =
        runTest {
            val engine = jsonResponder("{}")

            assertNotNull(
                runCatching { api(engine).getSessionMessages("session_1") }
                    .exceptionOrNull() as? SerializationException,
            )
        }

    @Test
    fun renameSession_putsSnakeCaseBody() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            var sent: String? = null
            val engine =
                jsonResponder("", HttpStatusCode.NoContent) { p, m, b ->
                    pathAndQuery = p
                    method = m
                    sent = b
                }

            val response = api(engine).renameSession("session_1", SessionRenameRequest("New title"))

            assertEquals("/api/sessions/session_1/rename", pathAndQuery)
            assertEquals("PUT", method)
            assertEquals("""{"title":"New title"}""", sent)
            assertTrue(response.isSuccessful)
        }

    @Test
    fun deleteSession_issuesDeleteAndToleratesEmptyBody() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            val engine =
                jsonResponder("", HttpStatusCode.NoContent) { p, m, _ ->
                    pathAndQuery = p
                    method = m
                }

            val response = api(engine).deleteSession("session_1")

            assertEquals("/api/sessions/session_1", pathAndQuery)
            assertEquals("DELETE", method)
            assertTrue(response.isSuccessful)
            assertEquals(Unit, response.body())
        }

    @Test
    fun getCronJobs_requestsApiCronJobsAndDecodesList() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            val payload =
                """[{"id":"job_1","name":"backup","schedule":"* * * * *",""" +
                    """"state":"active","last_run_status":null,"next_run":null}]"""
            val engine =
                jsonResponder(payload) { p, m, _ ->
                    pathAndQuery = p
                    method = m
                }

            val response = api(engine).getCronJobs()

            assertEquals("/api/cron/jobs", pathAndQuery)
            assertEquals("GET", method)
            assertEquals(1, response.body()?.size)
            assertEquals("job_1", response.body()?.get(0)?.id)
        }

    @Test
    fun pauseCronJob_postsAndAcceptsEmptyBody() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            val engine =
                jsonResponder("") { p, m, _ ->
                    pathAndQuery = p
                    method = m
                }

            val response = api(engine).pauseCronJob("job_1")

            assertEquals("/api/cron/jobs/job_1/pause", pathAndQuery)
            assertEquals("POST", method)
            assertTrue(response.isSuccessful)
        }

    @Test
    fun resumeCronJob_postsAndAcceptsEmptyBody() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            val engine =
                jsonResponder("") { p, m, _ ->
                    pathAndQuery = p
                    method = m
                }

            val response = api(engine).resumeCronJob("job_1")

            assertEquals("/api/cron/jobs/job_1/resume", pathAndQuery)
            assertEquals("POST", method)
            assertTrue(response.isSuccessful)
        }

    @Test
    fun triggerCronJob_postsAndAcceptsEmptyBody() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            val engine =
                jsonResponder("") { p, m, _ ->
                    pathAndQuery = p
                    method = m
                }

            val response = api(engine).triggerCronJob("job_1")

            assertEquals("/api/cron/jobs/job_1/trigger", pathAndQuery)
            assertEquals("POST", method)
            assertTrue(response.isSuccessful)
        }

    @Test
    fun deleteCronJob_sendsDeleteAndAcceptsEmptyBody() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            val engine =
                jsonResponder("") { p, m, _ ->
                    pathAndQuery = p
                    method = m
                }

            val response = api(engine).deleteCronJob("job_1")

            assertEquals("/api/cron/jobs/job_1", pathAndQuery)
            assertEquals("DELETE", method)
            assertTrue(response.isSuccessful)
        }

    @Test
    fun toggleSkill_putsSnakeCaseBodyAndAcceptsEmptyBody() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            var body: String? = null
            val engine =
                jsonResponder("") { p, m, b ->
                    pathAndQuery = p
                    method = m
                    body = b
                }

            val response = api(engine).toggleSkill(ToggleSkillRequest(name = "weather", enabled = true))

            assertEquals("/api/skills/toggle", pathAndQuery)
            assertEquals("PUT", method)
            assertEquals("""{"name":"weather","enabled":true}""", body)
            assertTrue(response.isSuccessful)
        }

    @Test
    fun getSkillContent_sendsNameAsQueryAndDecodes() =
        runTest {
            var pathAndQuery: String? = null
            val engine =
                jsonResponder("""{"name":"weather","content":"# weather"}""") { p, _, _ ->
                    pathAndQuery = p
                }

            val response = api(engine).getSkillContent("weather")

            assertEquals("/api/skills/content?name=weather", pathAndQuery)
            assertEquals("# weather", response.body()?.content)
        }

    @Test
    fun searchSkillsHub_omitsNullQueryParameters() =
        runTest {
            var pathAndQuery: String? = null
            val engine =
                jsonResponder("""{"results":[]}""") { p, _, _ ->
                    pathAndQuery = p
                }

            api(engine).searchSkillsHub("weather")

            assertEquals("/api/skills/hub/search?q=weather", pathAndQuery)
        }

    @Test
    fun searchSkillsHub_includesSourceAndLimitWhenGiven() =
        runTest {
            var pathAndQuery: String? = null
            val engine =
                jsonResponder("""{"results":[]}""") { p, _, _ ->
                    pathAndQuery = p
                }

            api(engine).searchSkillsHub("weather", source = "hub", limit = 5)

            assertEquals("/api/skills/hub/search?q=weather&source=hub&limit=5", pathAndQuery)
        }

    @Test
    fun getHubSources_decodesStringList() =
        runTest {
            val engine = jsonResponder("""["hub","local"]""")

            val response = api(engine).getHubSources()

            assertEquals(listOf("hub", "local"), response.body())
        }

    @Test
    fun rescanPlugins_requestsDashboardRescanAndAcceptsEmptyBody() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            val engine =
                jsonResponder("") { p, m, _ ->
                    pathAndQuery = p
                    method = m
                }

            val response = api(engine).rescanPlugins()

            assertEquals("/api/dashboard/plugins/rescan", pathAndQuery)
            assertEquals("GET", method)
            assertTrue(response.isSuccessful)
        }

    @Test
    fun getAchievements_decodesSnakeCaseCounters() =
        runTest {
            val engine = jsonResponder("""{"unlocked_count":3,"total_count":9,"is_stale":true}""")

            val response = api(engine).getAchievements()

            assertEquals(3, response.body()?.unlockedCount)
            assertEquals(9, response.body()?.totalCount)
            assertEquals(true, response.body()?.isStale)
        }

    @Test
    fun getRecentUnlocks_decodesList() =
        runTest {
            val engine =
                jsonResponder(
                    """[{"id":"a1","name":"First Light","unlocked":true,"discovered":true}]""",
                )

            val response = api(engine).getRecentUnlocks()

            assertEquals(1, response.body()?.size)
            assertEquals("First Light", response.body()?.get(0)?.name)
        }

    @Test
    fun switchKanbanBoard_postsSlugPathAndAcceptsEmptyBody() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            val engine =
                jsonResponder("") { p, m, _ ->
                    pathAndQuery = p
                    method = m
                }

            val response = api(engine).switchKanbanBoard("delivery")

            assertEquals("/api/plugins/kanban/boards/delivery/switch", pathAndQuery)
            assertEquals("POST", method)
            assertTrue(response.isSuccessful)
        }

    @Test
    fun updateKanbanTask_patchesJsonBody() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            var body: String? = null
            val engine =
                jsonResponder("") { p, m, b ->
                    pathAndQuery = p
                    method = m
                    body = b
                }

            val response = api(engine).updateKanbanTask("task_1", mapOf("status" to "done"))

            assertEquals("/api/plugins/kanban/tasks/task_1", pathAndQuery)
            assertEquals("PATCH", method)
            assertEquals("""{"status":"done"}""", body)
            assertTrue(response.isSuccessful)
        }

    @Test
    fun createKanbanTask_sendsBoardQueryAndDecodesTask() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            val engine =
                jsonResponder("""{"id":"t1","title":"Ship it","status":"todo"}""") { p, m, _ ->
                    pathAndQuery = p
                    method = m
                }

            val response = api(engine).createKanbanTask("delivery", CreateTaskBody("Ship it"))

            assertEquals("/api/plugins/kanban/tasks?board=delivery", pathAndQuery)
            assertEquals("POST", method)
            assertEquals("Ship it", response.body()?.title)
            assertEquals("todo", response.body()?.status)
        }

    @Test
    fun getMcpServers_requestsApiMcpServersAndDecodes() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            val engine =
                jsonResponder("""{"servers":[{"name":"srv","enabled":true}]}""") { p, m, _ ->
                    pathAndQuery = p
                    method = m
                }

            val response = api(engine).getMcpServers()

            assertEquals("/api/mcp/servers", pathAndQuery)
            assertEquals("GET", method)
            assertEquals("srv", response.body()?.servers?.get(0)?.name)
            assertEquals(true, response.body()?.servers?.get(0)?.enabled)
        }

    @Test
    fun toggleMcpServer_putsEnabledBodyAndAcceptsEmptyBody() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            var body: String? = null
            val engine =
                jsonResponder("") { p, m, b ->
                    pathAndQuery = p
                    method = m
                    body = b
                }

            val response = api(engine).toggleMcpServer("srv", McpServerToggleRequest(enabled = true))

            assertEquals("/api/mcp/servers/srv/enabled", pathAndQuery)
            assertEquals("PUT", method)
            assertEquals("""{"enabled":true}""", body)
            assertTrue(response.isSuccessful)
        }

    @Test
    fun testMcpServer_postsToTestPath() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            val engine =
                jsonResponder("""{"ok":true}""") { p, m, _ ->
                    pathAndQuery = p
                    method = m
                }

            val response = api(engine).testMcpServer("srv")

            assertEquals("/api/mcp/servers/srv/test", pathAndQuery)
            assertEquals("POST", method)
            assertEquals(true, response.body()?.ok)
        }

    @Test
    fun deleteMcpServer_sendsDeleteToServerName() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            val engine =
                jsonResponder("") { p, m, _ ->
                    pathAndQuery = p
                    method = m
                }

            val response = api(engine).deleteMcpServer("srv")

            assertEquals("/api/mcp/servers/srv", pathAndQuery)
            assertEquals("DELETE", method)
            assertTrue(response.isSuccessful)
        }

    @Test
    fun getMcpOAuthFlowStatus_requestsFlowPathAndDecodesSnakeCase() =
        runTest {
            var pathAndQuery: String? = null
            val engine =
                jsonResponder(
                    """{"flow_id":"f1","server_name":"srv","status":"pending"}""",
                ) { p, _, _ ->
                    pathAndQuery = p
                }

            val response = api(engine).getMcpOAuthFlowStatus("f1")

            assertEquals("/api/mcp/oauth/flows/f1", pathAndQuery)
            assertEquals("f1", response.body()?.flowId)
            assertEquals("srv", response.body()?.serverName)
            assertEquals("pending", response.body()?.status)
        }

    @Test
    fun getMcpCatalog_decodesEntries() =
        runTest {
            var pathAndQuery: String? = null
            val engine =
                jsonResponder("""{"entries":[]}""") { p, _, _ ->
                    pathAndQuery = p
                }

            val response = api(engine).getMcpCatalog()

            assertEquals("/api/mcp/catalog", pathAndQuery)
            assertEquals(0, response.body()?.entries?.size)
        }

    @Test
    fun installMcpCatalogEntry_postsNameAndDecodesJsonObject() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            var body: String? = null
            val engine =
                jsonResponder("""{"installed":true}""") { p, m, b ->
                    pathAndQuery = p
                    method = m
                    body = b
                }

            val response = api(engine).installMcpCatalogEntry(McpCatalogInstallRequest(name = "srv"))

            assertEquals("/api/mcp/catalog/install", pathAndQuery)
            assertEquals("POST", method)
            assertEquals("""{"name":"srv"}""", body)
            assertEquals(true, response.body()?.get("installed")?.toString()?.toBoolean())
        }

    @Test
    fun triggerBackup_postsAndDecodesArchive() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            val engine =
                jsonResponder("""{"archive":"hermes-backup.zip","ok":true}""") { p, m, _ ->
                    pathAndQuery = p
                    method = m
                }

            val response = api(engine).triggerBackup()

            assertEquals("/api/ops/backup", pathAndQuery)
            assertEquals("POST", method)
            assertEquals("hermes-backup.zip", response.body()?.archive)
            assertEquals(true, response.body()?.ok)
        }

    @Test
    fun runDoctor_postsToDoctorPath() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            val engine =
                jsonResponder("""{"ok":true,"name":"doctor"}""") { p, m, _ ->
                    pathAndQuery = p
                    method = m
                }

            val response = api(engine).runDoctor()

            assertEquals("/api/ops/doctor", pathAndQuery)
            assertEquals("POST", method)
            assertEquals("doctor", response.body()?.name)
            assertTrue(response.isSuccessful)
        }

    @Test
    fun updateSkillsFromHub_sendsEmptyJsonObjectByDefault() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            var body: String? = null
            val engine =
                jsonResponder("""{"ok":true}""") { p, m, b ->
                    pathAndQuery = p
                    method = m
                    body = b
                }

            val response = api(engine).updateSkillsFromHub()

            assertEquals("/api/skills/hub/update", pathAndQuery)
            assertEquals("POST", method)
            assertEquals("{}", body)
            assertEquals(true, response.body()?.ok)
        }

    @Test
    fun downloadBackup_passesRawBytesThroughWithoutJsonParsing() =
        runTest {
            var pathAndQuery: String? = null
            val engine =
                jsonResponder("PK-not-json-zip-payload") { p, _, _ ->
                    pathAndQuery = p
                }

            val response = api(engine).downloadBackup("hermes-backup.zip")

            assertEquals("/api/ops/backup/download?archive=hermes-backup.zip", pathAndQuery)
            assertEquals("PK-not-json-zip-payload", response.body()?.decodeToString())
        }

    @Test
    fun getCheckpoints_decodesTotals() =
        runTest {
            val engine = jsonResponder("""{"total_bytes":4096}""")

            val response = api(engine).getCheckpoints()

            assertEquals(4096L, response.body()?.total_bytes)
        }

    @Test
    fun pruneCheckpoints_postsToPrunePath() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            val engine =
                jsonResponder("""{"ok":true}""") { p, m, _ ->
                    pathAndQuery = p
                    method = m
                }

            api(engine).pruneCheckpoints()

            assertEquals("/api/ops/checkpoints/prune", pathAndQuery)
            assertEquals("POST", method)
        }

    @Test
    fun getHooks_decodesValidEvents() =
        runTest {
            val engine = jsonResponder("""{"hooks":[],"valid_events":["pre_commit"]}""")

            val response = api(engine).getHooks()

            assertEquals(listOf("pre_commit"), response.body()?.valid_events)
        }

    @Test
    fun deleteHook_sendsDeleteWithJsonBody() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            var body: String? = null
            val engine =
                jsonResponder("""{"deleted":true}""") { p, m, b ->
                    pathAndQuery = p
                    method = m
                    body = b
                }

            val response = api(engine).deleteHook(mapOf("id" to "h1"))

            assertEquals("/api/ops/hooks", pathAndQuery)
            assertEquals("DELETE", method)
            assertEquals("""{"id":"h1"}""", body)
            assertEquals(true, response.body()?.get("deleted")?.toString()?.toBoolean())
        }

    // ── system / analytics ────────────────────────────────────────────────

    @Test
    fun getSystemStats_requestsApiSystemStatsAndDecodes() =
        runTest {
            var pathAndQuery: String? = null
            val engine =
                jsonResponder("""{"os":"linux","hostname":"host","memory":{"percent":50.0}}""") { p, _, _ ->
                    pathAndQuery = p
                }

            val response = api(engine).getSystemStats()

            assertEquals("/api/system/stats", pathAndQuery)
            assertEquals("linux", response.body()?.os)
            assertEquals("host", response.body()?.hostname)
            assertEquals(50.0, response.body()?.memory?.percent)
        }

    @Test
    fun getAnalytics_forwardsDaysAndOptionalProfile() =
        runTest {
            var pathAndQuery: String? = null
            val engine =
                jsonResponder(
                    """{"daily":[],"by_model":[],"totals":{"total_input":0,"total_output":0,""" +
                        """"total_cache_read":0,"total_reasoning":0,"total_estimated_cost":0.0,""" +
                        """"total_actual_cost":0.0,"total_sessions":0,"total_api_calls":0},""" +
                        """"period_days":30,"skills":{"summary":{"total_skill_loads":0,""" +
                        """"total_skill_edits":0,"total_skill_actions":0,"distinct_skills_used":0},""" +
                        """"top_skills":[]},"tools":[]}""",
                ) { p, _, _ ->
                    pathAndQuery = p
                }

            val response = api(engine).getAnalytics(30, null)

            // Pinned by HermesApiServiceTest.testGetAnalytics_requestsCorrectPathAndQuery
            assertEquals("/api/analytics/usage?days=30", pathAndQuery)
            assertEquals(30, response.body()?.period_days)
        }

    @Test
    fun getModelsAnalytics_forwardsDaysAndProfile() =
        runTest {
            var pathAndQuery: String? = null
            val engine =
                jsonResponder(
                    """{"models":[{"model":"claude-opus","provider":"anthropic"}],""" +
                        """"totals":{"distinct_models":1,"total_input":0,"total_output":0,""" +
                        """"total_cache_read":0,"total_reasoning":0,"total_estimated_cost":0.0,""" +
                        """"total_actual_cost":0.0,"total_sessions":0,"total_api_calls":0},""" +
                        """"period_days":30}""",
                ) { p, _, _ ->
                    pathAndQuery = p
                }

            val response = api(engine).getModelsAnalytics(30, "work")

            // Pinned by HermesApiServiceTest.testGetModelsAnalytics_forwardsProfileParam
            assertEquals("/api/analytics/models?days=30&profile=work", pathAndQuery)
            assertEquals(30, response.body()?.period_days)
            assertEquals("claude-opus", response.body()?.models?.first()?.model)
        }

    // ── gateway control ───────────────────────────────────────────────────

    @Test
    fun startGateway_postsAndAcceptsEmptyBody() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            val engine =
                jsonResponder("") { p, m, _ ->
                    pathAndQuery = p
                    method = m
                }

            val response = api(engine).startGateway()

            assertEquals("/api/gateway/start", pathAndQuery)
            assertEquals("POST", method)
            assertTrue(response.isSuccessful)
        }

    @Test
    fun stopGateway_postsAndAcceptsEmptyBody() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            val engine =
                jsonResponder("") { p, m, _ ->
                    pathAndQuery = p
                    method = m
                }

            val response = api(engine).stopGateway()

            assertEquals("/api/gateway/stop", pathAndQuery)
            assertEquals("POST", method)
            assertTrue(response.isSuccessful)
        }

    @Test
    fun restartGateway_postsAndAcceptsEmptyBody() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            val engine =
                jsonResponder("") { p, m, _ ->
                    pathAndQuery = p
                    method = m
                }

            val response = api(engine).restartGateway()

            assertEquals("/api/gateway/restart", pathAndQuery)
            assertEquals("POST", method)
            assertTrue(response.isSuccessful)
        }

    // ── profiles ───────────────────────────────────────────────────────────

    @Test
    fun getProfiles_requestsApiProfilesAndDecodes() =
        runTest {
            var pathAndQuery: String? = null
            val engine =
                jsonResponder("""{"profiles":[{"name":"default","is_default":true}]}""") { p, _, _ ->
                    pathAndQuery = p
                }

            val response = api(engine).getProfiles()

            assertEquals("/api/profiles", pathAndQuery)
            assertEquals("default", response.body()?.profiles?.first()?.name)
        }

    @Test
    fun createProfile_postsCreateProfileRequestAndAcceptsEmptyBody() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            var body: String? = null
            val engine =
                jsonResponder("") { p, m, b ->
                    pathAndQuery = p
                    method = m
                    body = b
                }

            val response =
                api(engine).createProfile(CreateProfileRequest(name = "alpha", description = "my profile"))

            assertEquals("/api/profiles", pathAndQuery)
            assertEquals("POST", method)
            assertEquals("""{"name":"alpha","description":"my profile"}""", body)
            assertTrue(response.isSuccessful)
        }

    @Test
    fun getActiveProfile_decodesActiveName() =
        runTest {
            var pathAndQuery: String? = null
            val engine = jsonResponder("""{"active":"default"}""") { p, _, _ -> pathAndQuery = p }

            val response = api(engine).getActiveProfile()

            assertEquals("/api/profiles/active", pathAndQuery)
            assertEquals("default", response.body()?.active)
        }

    @Test
    fun setActiveProfile_postsNameAndAcceptsEmptyBody() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            var body: String? = null
            val engine =
                jsonResponder("") { p, m, b ->
                    pathAndQuery = p
                    method = m
                    body = b
                }

            val response = api(engine).setActiveProfile(SetActiveProfileRequest(name = "work"))

            assertEquals("/api/profiles/active", pathAndQuery)
            assertEquals("POST", method)
            assertEquals("""{"name":"work"}""", body)
            assertTrue(response.isSuccessful)
        }

    @Test
    fun getProfileSoul_decodesContent() =
        runTest {
            var pathAndQuery: String? = null
            val engine = jsonResponder("""{"content":"hello"}""") { p, _, _ -> pathAndQuery = p }

            val response = api(engine).getProfileSoul("default")

            assertEquals("/api/profiles/default/soul", pathAndQuery)
            assertEquals("hello", response.body()?.content)
        }

    @Test
    fun updateProfileSoul_putsContentAndAcceptsEmptyBody() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            var body: String? = null
            val engine =
                jsonResponder("") { p, m, b ->
                    pathAndQuery = p
                    method = m
                    body = b
                }

            val response =
                api(engine).updateProfileSoul("default", UpdateProfileSoulRequest(content = "v2"))

            assertEquals("/api/profiles/default/soul", pathAndQuery)
            assertEquals("PUT", method)
            assertEquals("""{"content":"v2"}""", body)
            assertTrue(response.isSuccessful)
        }

    @Test
    fun updateProfileModel_putsProviderAndModelAndAcceptsEmptyBody() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            var body: String? = null
            val engine =
                jsonResponder("") { p, m, b ->
                    pathAndQuery = p
                    method = m
                    body = b
                }

            val response =
                api(engine).updateProfileModel(
                    "default",
                    UpdateProfileModelRequest(provider = "openai", model = "gpt-4o"),
                )

            assertEquals("/api/profiles/default/model", pathAndQuery)
            assertEquals("PUT", method)
            assertEquals("""{"provider":"openai","model":"gpt-4o"}""", body)
            assertTrue(response.isSuccessful)
        }

    @Test
    fun updateProfileDescription_putsDescriptionAndAcceptsEmptyBody() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            var body: String? = null
            val engine =
                jsonResponder("") { p, m, b ->
                    pathAndQuery = p
                    method = m
                    body = b
                }

            val response =
                api(engine).updateProfileDescription(
                    "default",
                    UpdateProfileDescriptionRequest(description = "alt"),
                )

            assertEquals("/api/profiles/default/description", pathAndQuery)
            assertEquals("PUT", method)
            assertEquals("""{"description":"alt"}""", body)
            assertTrue(response.isSuccessful)
        }

    // ── toolsets ──────────────────────────────────────────────────────────

    @Test
    fun getToolsets_decodesList() =
        runTest {
            var pathAndQuery: String? = null
            val engine =
                jsonResponder("""[{"name":"t1","enabled":true}]""") { p, _, _ -> pathAndQuery = p }

            val response = api(engine).getToolsets()

            assertEquals("/api/tools/toolsets", pathAndQuery)
            assertEquals("t1", response.body()?.first()?.name)
            assertEquals(true, response.body()?.first()?.enabled)
        }

    @Test
    fun toggleToolset_putsEnabledAndAcceptsEmptyBody() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            var body: String? = null
            val engine =
                jsonResponder("") { p, m, b ->
                    pathAndQuery = p
                    method = m
                    body = b
                }

            val response =
                api(engine).toggleToolset("t1", ToolsetToggleRequest(enabled = true))

            assertEquals("/api/tools/toolsets/t1", pathAndQuery)
            assertEquals("PUT", method)
            assertEquals("""{"enabled":true}""", body)
            assertTrue(response.isSuccessful)
        }

    // ── config ────────────────────────────────────────────────────────────

    @Test
    fun getRawConfig_decodesYaml() =
        runTest {
            var pathAndQuery: String? = null
            val engine =
                jsonResponder("""{"path":"/tmp/c.yaml","yaml":"x: 1"}""") { p, _, _ -> pathAndQuery = p }

            val response = api(engine).getRawConfig()

            assertEquals("/api/config/raw", pathAndQuery)
            assertEquals("x: 1", response.body()?.yaml)
        }

    @Test
    fun updateRawConfig_putsYamlTextAndAcceptsEmptyBody() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            var body: String? = null
            val engine =
                jsonResponder("") { p, m, b ->
                    pathAndQuery = p
                    method = m
                    body = b
                }

            val response =
                api(engine).updateRawConfig(UpdateRawConfigRequest(yaml_text = "x: 1"))

            assertEquals("/api/config/raw", pathAndQuery)
            assertEquals("PUT", method)
            assertEquals("""{"yaml_text":"x: 1"}""", body)
            assertTrue(response.isSuccessful)
        }

    @Test
    fun getConfig_decodesJsonObject() =
        runTest {
            var pathAndQuery: String? = null
            val engine =
                jsonResponder("""{"a":1,"b":"x"}""") { p, _, _ -> pathAndQuery = p }

            val response = api(engine).getConfig()

            assertEquals("/api/config", pathAndQuery)
            assertEquals("1", response.body()?.get("a")?.toString()?.trim('"'))
        }

    @Test
    fun getConfigSchema_decodesFields() =
        runTest {
            var pathAndQuery: String? = null
            val engine =
                jsonResponder("""{"fields":{},"category_order":["network"]}""") { p, _, _ ->
                    pathAndQuery = p
                }

            val response = api(engine).getConfigSchema()

            assertEquals("/api/config/schema", pathAndQuery)
            assertEquals(listOf("network"), response.body()?.category_order)
        }

    @Test
    fun getConfigDefaults_decodesJsonObject() =
        runTest {
            var pathAndQuery: String? = null
            val engine = jsonResponder("""{"a":1}""") { p, _, _ -> pathAndQuery = p }

            val response = api(engine).getConfigDefaults()

            assertEquals("/api/config/defaults", pathAndQuery)
            assertEquals("1", response.body()?.get("a")?.toString()?.trim('"'))
        }

    @Test
    fun updateConfig_putsConfigAndAcceptsEmptyBody() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            var body: String? = null
            val engine =
                jsonResponder("") { p, m, b ->
                    pathAndQuery = p
                    method = m
                    body = b
                }

            val response =
                api(engine).updateConfig(ConfigUpdateRequest(config = emptyMap()))

            assertEquals("/api/config", pathAndQuery)
            assertEquals("PUT", method)
            assertEquals("""{"config":{}}""", body)
            assertTrue(response.isSuccessful)
        }

    // ── webhooks ──────────────────────────────────────────────────────────

    @Test
    fun getWebhooks_decodesEnabledAndBaseUrl() =
        runTest {
            var pathAndQuery: String? = null
            val engine =
                jsonResponder("""{"enabled":true,"base_url":"https://x.example"}""") { p, _, _ ->
                    pathAndQuery = p
                }

            val response = api(engine).getWebhooks()

            assertEquals("/api/webhooks", pathAndQuery)
            assertEquals(true, response.body()?.enabled)
            assertEquals("https://x.example", response.body()?.base_url)
        }

    @Test
    fun toggleWebhooks_postsEnabledAndAcceptsEmptyBody() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            var body: String? = null
            val engine =
                jsonResponder("") { p, m, b ->
                    pathAndQuery = p
                    method = m
                    body = b
                }

            val response = api(engine).toggleWebhooks(WebhooksToggleRequest(enabled = false))

            assertEquals("/api/webhooks/enable", pathAndQuery)
            assertEquals("POST", method)
            assertEquals("""{"enabled":false}""", body)
            assertTrue(response.isSuccessful)
        }

    @Test
    fun createWebhook_postsBodyAndDecodesSubscription() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            var body: String? = null
            val engine =
                jsonResponder("""{"name":"w1","url":"https://x.example/hook"}""") { p, m, b ->
                    pathAndQuery = p
                    method = m
                    body = b
                }

            val response =
                api(engine).createWebhook(CreateWebhookRequest(name = "w1"))

            assertEquals("/api/webhooks", pathAndQuery)
            assertEquals("POST", method)
            assertEquals("""{"name":"w1"}""", body)
            assertEquals("w1", response.body()?.name)
        }

    @Test
    fun deleteWebhook_sendsDeleteAndDecodesOk() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            val engine =
                jsonResponder("""{"ok":true}""") { p, m, _ ->
                    pathAndQuery = p
                    method = m
                }

            val response = api(engine).deleteWebhook("w1")

            assertEquals("/api/webhooks/w1", pathAndQuery)
            assertEquals("DELETE", method)
            assertEquals(true, response.body()?.ok)
        }

    @Test
    fun setWebhookEnabled_putsEnabledAndAcceptsEmptyBody() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            var body: String? = null
            val engine =
                jsonResponder("") { p, m, b ->
                    pathAndQuery = p
                    method = m
                    body = b
                }

            val response =
                api(engine).setWebhookEnabled(
                    "w1",
                    WebhookToggleSubscriptionRequest(enabled = true),
                )

            assertEquals("/api/webhooks/w1/enabled", pathAndQuery)
            assertEquals("PUT", method)
            assertEquals("""{"enabled":true}""", body)
            assertTrue(response.isSuccessful)
        }

    // ── model ─────────────────────────────────────────────────────────────

    @Test
    fun getModelInfo_decodesModelAndProvider() =
        runTest {
            var pathAndQuery: String? = null
            val engine =
                jsonResponder("""{"model":"gpt-4o","provider":"openai"}""") { p, _, _ ->
                    pathAndQuery = p
                }

            val response = api(engine).getModelInfo()

            assertEquals("/api/model/info", pathAndQuery)
            assertEquals("gpt-4o", response.body()?.model)
            assertEquals("openai", response.body()?.provider)
        }

    @Test
    fun getModelOptions_sendsDefaults() =
        runTest {
            var pathAndQuery: String? = null
            val engine = jsonResponder("""{"providers":[]}""") { p, _, _ -> pathAndQuery = p }

            val response = api(engine).getModelOptions()

            assertEquals(
                "/api/model/options?refresh=false&include_unconfigured=true",
                pathAndQuery,
            )
            assertEquals(0, response.body()?.providers?.size)
        }

    @Test
    fun getAuxiliaryModels_decodesMain() =
        runTest {
            var pathAndQuery: String? = null
            val engine =
                jsonResponder(
                    """{"tasks":[],"main":{"provider":"openai","model":"gpt-4o"}}""",
                ) { p, _, _ -> pathAndQuery = p }

            val response = api(engine).getAuxiliaryModels()

            assertEquals("/api/model/auxiliary", pathAndQuery)
            assertEquals("openai", response.body()?.main?.provider)
        }

    @Test
    fun getMoaModels_decodesDefaultPreset() =
        runTest {
            var pathAndQuery: String? = null
            val engine =
                jsonResponder("""{"default_preset":"p","presets":{}}""") { p, _, _ ->
                    pathAndQuery = p
                }

            val response = api(engine).getMoaModels()

            assertEquals("/api/model/moa", pathAndQuery)
            assertEquals("p", response.body()?.default_preset)
        }

    @Test
    fun saveMoaModels_putsBodyAndDecodes() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            val engine =
                jsonResponder("""{"default_preset":"p"}""") { p, m, _ ->
                    pathAndQuery = p
                    method = m
                }

            val response = api(engine).saveMoaModels(com.m57.hermescontrol.data.model.MoaConfigResponse())

            assertEquals("/api/model/moa", pathAndQuery)
            assertEquals("PUT", method)
            assertEquals("p", response.body()?.default_preset)
        }

    @Test
    fun setModelAssignment_postsAndDecodesOk() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            val engine =
                jsonResponder("""{"ok":true,"scope":"global"}""") { p, m, _ ->
                    pathAndQuery = p
                    method = m
                }

            val response =
                api(engine).setModelAssignment(
                    com.m57.hermescontrol.data.model.ModelAssignmentRequest(
                        confirm_expensive_model = false,
                        scope = "global",
                        provider = "openai",
                        model = "gpt-4o",
                    ),
                )

            assertEquals("/api/model/set", pathAndQuery)
            assertEquals("POST", method)
            assertEquals(true, response.body()?.ok)
        }

    // ── logs ──────────────────────────────────────────────────────────────

    @Test
    fun getLogs_sendsAllDefaults() =
        runTest {
            var pathAndQuery: String? = null
            val engine = jsonResponder("""{"lines":[]}""") { p, _, _ -> pathAndQuery = p }

            val response = api(engine).getLogs()

            assertEquals("/api/logs?file=agent&lines=100&level=ALL&component=all", pathAndQuery)
            assertEquals(0, response.body()?.lines?.size)
        }

    @Test
    fun getLogs_honorsFileAndLines() =
        runTest {
            var pathAndQuery: String? = null
            val engine = jsonResponder("""{"lines":[]}""") { p, _, _ -> pathAndQuery = p }

            api(engine).getLogs(file = "gateway", lines = 50)

            assertEquals("/api/logs?file=gateway&lines=50&level=ALL&component=all", pathAndQuery)
        }

    // ── dashboard plugins ─────────────────────────────────────────────────

    @Test
    fun getPlugins_decodesList() =
        runTest {
            var pathAndQuery: String? = null
            val engine =
                jsonResponder("""{"plugins":[{"name":"p1","has_dashboard_manifest":false}]}""") { p, _, _ ->
                    pathAndQuery = p
                }

            val response = api(engine).getPlugins()

            assertEquals("/api/dashboard/plugins/hub", pathAndQuery)
            assertEquals("p1", response.body()?.plugins?.first()?.name)
        }

    @Test
    fun installPlugin_postsBodyAndAcceptsEmptyBody() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            var body: String? = null
            val engine =
                jsonResponder("") { p, m, b ->
                    pathAndQuery = p
                    method = m
                    body = b
                }

            val response =
                api(engine).installPlugin(AgentPluginInstallBody(identifier = "owner/repo"))

            assertEquals("/api/dashboard/agent-plugins/install", pathAndQuery)
            assertEquals("POST", method)
            assertEquals("""{"identifier":"owner/repo"}""", body)
            assertTrue(response.isSuccessful)
        }

    @Test
    fun uninstallPlugin_sendsDeleteAndAcceptsEmptyBody() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            val engine =
                jsonResponder("") { p, m, _ ->
                    pathAndQuery = p
                    method = m
                }

            val response = api(engine).uninstallPlugin("owner/repo")

            assertEquals("/api/dashboard/agent-plugins/owner/repo", pathAndQuery)
            assertEquals("DELETE", method)
            assertTrue(response.isSuccessful)
        }

    @Test
    fun updatePlugin_postsAndAcceptsEmptyBody() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            val engine =
                jsonResponder("") { p, m, _ ->
                    pathAndQuery = p
                    method = m
                }

            val response = api(engine).updatePlugin("owner/repo")

            assertEquals("/api/dashboard/agent-plugins/owner/repo/update", pathAndQuery)
            assertEquals("POST", method)
            assertTrue(response.isSuccessful)
        }

    @Test
    fun enablePlugin_postsAndAcceptsEmptyBody() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            val engine =
                jsonResponder("") { p, m, _ ->
                    pathAndQuery = p
                    method = m
                }

            val response = api(engine).enablePlugin("owner/repo")

            assertEquals("/api/dashboard/agent-plugins/owner/repo/enable", pathAndQuery)
            assertEquals("POST", method)
            assertTrue(response.isSuccessful)
        }

    @Test
    fun disablePlugin_postsAndAcceptsEmptyBody() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            val engine =
                jsonResponder("") { p, m, _ ->
                    pathAndQuery = p
                    method = m
                }

            val response = api(engine).disablePlugin("owner/repo")

            assertEquals("/api/dashboard/agent-plugins/owner/repo/disable", pathAndQuery)
            assertEquals("POST", method)
            assertTrue(response.isSuccessful)
        }

    @Test
    fun savePluginProviders_putsAndAcceptsEmptyBody() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            var body: String? = null
            val engine =
                jsonResponder("") { p, m, b ->
                    pathAndQuery = p
                    method = m
                    body = b
                }

            val response =
                api(engine).savePluginProviders(
                    PluginProvidersPutRequest(memoryProvider = "graphiti"),
                )

            assertEquals("/api/dashboard/plugin-providers", pathAndQuery)
            assertEquals("PUT", method)
            assertEquals("""{"memory_provider":"graphiti"}""", body)
            assertTrue(response.isSuccessful)
        }

    @Test
    fun setPluginVisibility_postsAndAcceptsEmptyBody() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            var body: String? = null
            val engine =
                jsonResponder("") { p, m, b ->
                    pathAndQuery = p
                    method = m
                    body = b
                }

            val response =
                api(engine).setPluginVisibility(
                    "owner/repo",
                    mapOf("hidden" to true),
                )

            assertEquals("/api/dashboard/plugins/owner/repo/visibility", pathAndQuery)
            assertEquals("POST", method)
            assertEquals("""{"hidden":true}""", body)
            assertTrue(response.isSuccessful)
        }

    // ── messaging platforms ───────────────────────────────────────────────

    @Test
    fun getMessagingPlatforms_decodesPlatforms() =
        runTest {
            var pathAndQuery: String? = null
            val engine =
                jsonResponder(
                    """{"env_path":"/tmp/e","gateway_start_command":"start","platforms":[]}""",
                ) { p, _, _ -> pathAndQuery = p }

            val response = api(engine).getMessagingPlatforms()

            assertEquals("/api/messaging/platforms", pathAndQuery)
            assertEquals("/tmp/e", response.body()?.envPath)
            assertEquals("start", response.body()?.gatewayStartCommand)
        }

    @Test
    fun configurePlatform_putsBodyAndAcceptsEmptyBody() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            var body: String? = null
            val engine =
                jsonResponder("") { p, m, b ->
                    pathAndQuery = p
                    method = m
                    body = b
                }

            val response =
                api(engine).configurePlatform(
                    "telegram",
                    MessagingPlatformUpdate(enabled = true),
                )

            assertEquals("/api/messaging/platforms/telegram", pathAndQuery)
            assertEquals("PUT", method)
            assertEquals("""{"enabled":true}""", body)
            assertTrue(response.isSuccessful)
        }

    @Test
    fun testMessagingPlatform_postsAndDecodesOk() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            val engine =
                jsonResponder("""{"ok":true,"state":"ok","message":"running"}""") { p, m, _ ->
                    pathAndQuery = p
                    method = m
                }

            val response = api(engine).testMessagingPlatform("telegram")

            assertEquals("/api/messaging/platforms/telegram/test", pathAndQuery)
            assertEquals("POST", method)
            assertEquals(true, response.body()?.ok)
            assertEquals("running", response.body()?.message)
        }

    @Test
    fun removeMessagingPlatform_sendsDeleteAndAcceptsEmptyBody() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            val engine =
                jsonResponder("") { p, m, _ ->
                    pathAndQuery = p
                    method = m
                }

            val response = api(engine).removeMessagingPlatform("telegram")

            assertEquals("/api/messaging/platforms/telegram", pathAndQuery)
            assertEquals("DELETE", method)
            assertTrue(response.isSuccessful)
        }

    @Test
    fun startTelegramOnboarding_postsAndDecodes() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            val engine =
                jsonResponder(
                    """{"pairing_id":"p1","suggested_username":"bot","deep_link":"https://t.me/x",""" +
                        """"qr_payload":"q","expires_at":"2026-01-01"}""",
                ) { p, m, _ ->
                    pathAndQuery = p
                    method = m
                }

            val response =
                api(engine).startTelegramOnboarding(
                    TelegramOnboardingStartRequest(botName = "Hermes"),
                )

            assertEquals("/api/messaging/telegram/onboarding/start", pathAndQuery)
            assertEquals("POST", method)
            assertEquals("p1", response.body()?.pairingId)
            assertEquals("bot", response.body()?.suggestedUsername)
        }

    @Test
    fun getTelegramOnboardingStatus_decodesStatus() =
        runTest {
            var pathAndQuery: String? = null
            val engine =
                jsonResponder("""{"status":"pending"}""") { p, _, _ -> pathAndQuery = p }

            val response = api(engine).getTelegramOnboardingStatus("p1")

            assertEquals("/api/messaging/telegram/onboarding/p1", pathAndQuery)
            assertEquals("pending", response.body()?.status)
        }

    @Test
    fun applyTelegramOnboarding_postsAndDecodes() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            val engine =
                jsonResponder(
                    """{"ok":true,"platform":"telegram","needs_restart":false}""",
                ) { p, m, _ ->
                    pathAndQuery = p
                    method = m
                }

            val response =
                api(engine).applyTelegramOnboarding(
                    "p1",
                    TelegramOnboardingApplyRequest(allowedUserIds = listOf("u1")),
                )

            assertEquals("/api/messaging/telegram/onboarding/p1/apply", pathAndQuery)
            assertEquals("POST", method)
            assertEquals(true, response.body()?.ok)
            assertEquals("telegram", response.body()?.platform)
        }

    @Test
    fun cancelTelegramOnboarding_sendsDeleteAndAcceptsEmptyBody() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            val engine =
                jsonResponder("") { p, m, _ ->
                    pathAndQuery = p
                    method = m
                }

            val response = api(engine).cancelTelegramOnboarding("p1")

            assertEquals("/api/messaging/telegram/onboarding/p1", pathAndQuery)
            assertEquals("DELETE", method)
            assertTrue(response.isSuccessful)
        }

    // ── OAuth providers ───────────────────────────────────────────────────

    @Test
    fun getOAuthProviders_decodesList() =
        runTest {
            var pathAndQuery: String? = null
            val engine =
                jsonResponder("""{"providers":[{"id":"google","name":"Google","flow":"pkce"}]}""") { p, _, _ ->
                    pathAndQuery = p
                }

            val response = api(engine).getOAuthProviders()

            assertEquals("/api/providers/oauth", pathAndQuery)
            assertEquals("google", response.body()?.providers?.first()?.id)
        }

    @Test
    fun disconnectOAuthProvider_sendsDeleteAndAcceptsEmptyBody() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            val engine =
                jsonResponder("") { p, m, _ ->
                    pathAndQuery = p
                    method = m
                }

            val response = api(engine).disconnectOAuthProvider("google")

            assertEquals("/api/providers/oauth/google", pathAndQuery)
            assertEquals("DELETE", method)
            assertTrue(response.isSuccessful)
        }

    @Test
    fun startOAuthLogin_postsAndDecodes() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            val engine =
                jsonResponder(
                    """{"session_id":"s1","flow":"pkce","auth_url":"https://x","expires_in":600}""",
                ) { p, m, _ ->
                    pathAndQuery = p
                    method = m
                }

            val response = api(engine).startOAuthLogin("google")

            assertEquals("/api/providers/oauth/google/start", pathAndQuery)
            assertEquals("POST", method)
            assertEquals("s1", response.body()?.sessionId)
            assertEquals("pkce", response.body()?.flow)
        }

    @Test
    fun submitOAuthCode_postsAndDecodes() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            var body: String? = null
            val engine =
                jsonResponder("""{"ok":true}""") { p, m, b ->
                    pathAndQuery = p
                    method = m
                    body = b
                }

            val response =
                api(engine).submitOAuthCode(
                    "google",
                    com.m57.hermescontrol.data.model.OAuthSubmitRequest(
                        sessionId = "s1",
                        code = "abcd",
                    ),
                )

            assertEquals("/api/providers/oauth/google/submit", pathAndQuery)
            assertEquals("POST", method)
            assertEquals("""{"session_id":"s1","code":"abcd"}""", body)
            assertEquals(true, response.body()?.ok)
        }

    @Test
    fun pollOAuthSession_decodes() =
        runTest {
            var pathAndQuery: String? = null
            val engine =
                jsonResponder("""{"session_id":"s1","status":"pending"}""") { p, _, _ -> pathAndQuery = p }

            val response = api(engine).pollOAuthSession("google", "s1")

            assertEquals("/api/providers/oauth/google/poll/s1", pathAndQuery)
            assertEquals("pending", response.body()?.status)
        }

    @Test
    fun cancelOAuthSession_sendsDeleteOnSessionsPathAndAcceptsEmptyBody() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            val engine =
                jsonResponder("") { p, m, _ ->
                    pathAndQuery = p
                    method = m
                }

            val response = api(engine).cancelOAuthSession("s1")

            assertEquals("/api/providers/oauth/sessions/s1", pathAndQuery)
            assertEquals("DELETE", method)
            assertTrue(response.isSuccessful)
        }

    // ── env vars ──────────────────────────────────────────────────────────

    @Test
    fun getEnvVars_decodesMap() =
        runTest {
            var pathAndQuery: String? = null
            val engine =
                jsonResponder(
                    """{"FOO":{"is_set":true,"is_password":false,"redacted_value":"***"}}""",
                ) { p, _, _ -> pathAndQuery = p }

            val response = api(engine).getEnvVars()

            assertEquals("/api/env", pathAndQuery)
            assertEquals(true, response.body()?.get("FOO")?.isSet)
            assertEquals(false, response.body()?.get("FOO")?.isPassword)
        }

    @Test
    fun updateEnvVar_putsAndAcceptsEmptyBody() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            var body: String? = null
            val engine =
                jsonResponder("") { p, m, b ->
                    pathAndQuery = p
                    method = m
                    body = b
                }

            val response =
                api(engine).updateEnvVar(EnvVarUpdate(key = "FOO", value = "v"))

            assertEquals("/api/env", pathAndQuery)
            assertEquals("PUT", method)
            assertEquals("""{"key":"FOO","value":"v"}""", body)
            assertTrue(response.isSuccessful)
        }

    @Test
    fun revealEnvVar_postsAndDecodes() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            val engine =
                jsonResponder("""{"key":"FOO","value":"secret"}""") { p, m, _ ->
                    pathAndQuery = p
                    method = m
                }

            val response =
                api(engine).revealEnvVar(EnvVarRevealRequest(key = "FOO"))

            assertEquals("/api/env/reveal", pathAndQuery)
            assertEquals("POST", method)
            assertEquals("secret", response.body()?.value)
        }

    @Test
    fun deleteEnvVar_sendsDeleteWithBodyAndAcceptsEmptyBody() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            var body: String? = null
            val engine =
                jsonResponder("") { p, m, b ->
                    pathAndQuery = p
                    method = m
                    body = b
                }

            val response = api(engine).deleteEnvVar(EnvVarDeleteRequest(key = "FOO"))

            assertEquals("/api/env", pathAndQuery)
            assertEquals("DELETE", method)
            assertEquals("""{"key":"FOO"}""", body)
            assertTrue(response.isSuccessful)
        }

    // ── admin: Hermes update ──────────────────────────────────────────────

    @Test
    fun checkHermesUpdate_sendsDefaultForceFalse() =
        runTest {
            var pathAndQuery: String? = null
            val engine =
                jsonResponder("""{"update_available":false,"can_apply":false}""") { p, _, _ ->
                    pathAndQuery = p
                }

            val response = api(engine).checkHermesUpdate()

            assertEquals("/api/hermes/update/check?force=false", pathAndQuery)
            assertEquals(false, response.body()?.update_available)
        }

    @Test
    fun updateHermes_postsAndDecodes() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            val engine =
                jsonResponder("""{"ok":true}""") { p, m, _ ->
                    pathAndQuery = p
                    method = m
                }

            val response = api(engine).updateHermes()

            assertEquals("/api/hermes/update", pathAndQuery)
            assertEquals("POST", method)
            assertEquals(true, response.body()?.ok)
        }

    @Test
    fun getUpdateReceipt_decodes() =
        runTest {
            var pathAndQuery: String? = null
            val engine =
                jsonResponder("""{"receipt":{"outcome":"success"}}""") { p, _, _ ->
                    pathAndQuery = p
                }

            val response = api(engine).getUpdateReceipt()

            assertEquals("/api/hermes/update/receipt", pathAndQuery)
            assertEquals("success", response.body()?.receipt?.outcome)
        }

    // ── admin: portal / learning ──────────────────────────────────────────

    @Test
    fun getPortal_decodesLoggedIn() =
        runTest {
            var pathAndQuery: String? = null
            val engine =
                jsonResponder("""{"logged_in":true,"portal_url":"https://x.example"}""") { p, _, _ ->
                    pathAndQuery = p
                }

            val response = api(engine).getPortal()

            assertEquals("/api/portal", pathAndQuery)
            assertEquals(true, response.body()?.logged_in)
            assertEquals("https://x.example", response.body()?.portal_url)
        }

    @Test
    fun getLearningGraph_omitsNullProfile() =
        runTest {
            var pathAndQuery: String? = null
            val engine =
                jsonResponder("""{"nodes":[],"edges":[],"memory":[]}""") { p, _, _ -> pathAndQuery = p }

            val response = api(engine).getLearningGraph()

            assertEquals("/api/learning/graph", pathAndQuery)
            assertEquals(0, response.body()?.nodes?.size)
        }

    @Test
    fun getLearningGraph_forwardsProfile() =
        runTest {
            var pathAndQuery: String? = null
            val engine =
                jsonResponder("""{"nodes":[],"edges":[],"memory":[]}""") { p, _, _ -> pathAndQuery = p }

            api(engine).getLearningGraph("work")

            assertEquals("/api/learning/graph?profile=work", pathAndQuery)
        }

    // ── admin: curator ────────────────────────────────────────────────────

    @Test
    fun getCurator_decodesEnabled() =
        runTest {
            var pathAndQuery: String? = null
            val engine =
                jsonResponder("""{"enabled":true,"paused":false}""") { p, _, _ ->
                    pathAndQuery = p
                }

            val response = api(engine).getCurator()

            assertEquals("/api/curator", pathAndQuery)
            assertEquals(true, response.body()?.enabled)
        }

    @Test
    fun setCuratorPaused_putsAndDecodes() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            var body: String? = null
            val engine =
                jsonResponder("""{"ok":true}""") { p, m, b ->
                    pathAndQuery = p
                    method = m
                    body = b
                }

            val response =
                api(engine).setCuratorPaused(buildJsonObject { put("paused", true) })

            assertEquals("/api/curator/paused", pathAndQuery)
            assertEquals("PUT", method)
            assertEquals("""{"paused":true}""", body)
            assertEquals(true, response.body()?.get("ok")?.toString()?.toBoolean())
        }

    @Test
    fun runCurator_postsAndDecodes() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            val engine =
                jsonResponder("""{"ok":true}""") { p, m, _ ->
                    pathAndQuery = p
                    method = m
                }

            val response = api(engine).runCurator()

            assertEquals("/api/curator/run", pathAndQuery)
            assertEquals("POST", method)
            assertEquals(true, response.body()?.ok)
        }

    // ── admin: memory ─────────────────────────────────────────────────────

    @Test
    fun getMemory_decodesActive() =
        runTest {
            var pathAndQuery: String? = null
            val engine = jsonResponder("""{"active":"MEMORY.md"}""") { p, _, _ -> pathAndQuery = p }

            val response = api(engine).getMemory()

            assertEquals("/api/memory", pathAndQuery)
            assertEquals("MEMORY.md", response.body()?.active)
        }

    @Test
    fun resetMemory_postsAndDecodes() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            var body: String? = null
            val engine =
                jsonResponder("""{"ok":true,"deleted":["MEMORY.md"]}""") { p, m, b ->
                    pathAndQuery = p
                    method = m
                    body = b
                }

            val response = api(engine).resetMemory(MemoryResetRequest(target = "memory"))

            // Pinned by HermesApiServiceTest.testResetMemory_postsSerializableRequestAndParsesResponse
            assertEquals("/api/memory/reset", pathAndQuery)
            assertEquals("POST", method)
            assertEquals("""{"target":"memory"}""", body)
            assertEquals(true, response.body()?.ok)
            assertEquals(listOf("MEMORY.md"), response.body()?.deleted)
        }

    // ── admin: credential pool ────────────────────────────────────────────

    @Test
    fun getCredentialPool_decodes() =
        runTest {
            var pathAndQuery: String? = null
            val engine = jsonResponder("""{"providers":[]}""") { p, _, _ -> pathAndQuery = p }

            val response = api(engine).getCredentialPool()

            assertEquals("/api/credentials/pool", pathAndQuery)
            assertEquals(0, response.body()?.providers?.size)
        }

    @Test
    fun addCredentialPoolEntry_postsAndDecodes() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            var body: String? = null
            val engine =
                jsonResponder("""{"ok":true}""") { p, m, b ->
                    pathAndQuery = p
                    method = m
                    body = b
                }

            val response =
                api(engine).addCredentialPoolEntry(mapOf("provider" to "openai", "token" to "x"))

            assertEquals("/api/credentials/pool", pathAndQuery)
            assertEquals("POST", method)
            assertEquals("""{"provider":"openai","token":"x"}""", body)
            assertEquals(true, response.body()?.get("ok")?.toString()?.toBoolean())
        }

    @Test
    fun removeCredentialPoolEntry_sendsDeleteAndDecodes() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            val engine =
                jsonResponder("""{"ok":true}""") { p, m, _ ->
                    pathAndQuery = p
                    method = m
                }

            val response = api(engine).removeCredentialPoolEntry("openai", 0)

            assertEquals("/api/credentials/pool/openai/0", pathAndQuery)
            assertEquals("DELETE", method)
            assertEquals(true, response.body()?.get("ok")?.toString()?.toBoolean())
        }

    // ── admin: action status ──────────────────────────────────────────────

    @Test
    fun getActionStatus_requestsPathWithDefaultLines() =
        runTest {
            var pathAndQuery: String? = null
            val engine =
                jsonResponder("""{"name":"doctor","running":false}""") { p, _, _ ->
                    pathAndQuery = p
                }

            val response = api(engine).getActionStatus("doctor")

            assertEquals("/api/actions/doctor/status?lines=200", pathAndQuery)
            assertEquals("doctor", response.body()?.name)
            assertEquals(false, response.body()?.running)
        }

    @Test
    fun getActionStatus_honorsLinesOverride() =
        runTest {
            var pathAndQuery: String? = null
            val engine = jsonResponder("""{"name":"doctor"}""") { p, _, _ -> pathAndQuery = p }

            api(engine).getActionStatus("doctor", lines = 50)

            assertEquals("/api/actions/doctor/status?lines=50", pathAndQuery)
        }

    // ── managed files ─────────────────────────────────────────────────────

    @Test
    fun listManagedFiles_omitsNullPath() =
        runTest {
            var pathAndQuery: String? = null
            val engine = jsonResponder("""{"path":"/"}""") { p, _, _ -> pathAndQuery = p }

            val response = api(engine).listManagedFiles()

            assertEquals("/api/files", pathAndQuery)
            assertEquals("/", response.body()?.path)
        }

    @Test
    fun listManagedFiles_forwardsPath() =
        runTest {
            var pathAndQuery: String? = null
            val engine = jsonResponder("""{"path":"/docs"}""") { p, _, _ -> pathAndQuery = p }

            api(engine).listManagedFiles("/docs")

            assertEquals("/api/files?path=%2Fdocs", pathAndQuery)
        }

    @Test
    fun readManagedFile_forwardsPath() =
        runTest {
            var pathAndQuery: String? = null
            val engine =
                jsonResponder("""{"name":"x","path":"/x","size":1}""") { p, _, _ -> pathAndQuery = p }

            val response = api(engine).readManagedFile("/x")

            assertEquals("/api/files/read?path=%2Fx", pathAndQuery)
            assertEquals("x", response.body()?.name)
        }

    @Test
    fun downloadManagedFile_passesRawBytesThroughWithoutJsonParsing() =
        runTest {
            var pathAndQuery: String? = null
            val engine =
                jsonResponder("binary-not-json-payload") { p, _, _ -> pathAndQuery = p }

            val response = api(engine).downloadManagedFile("/x.bin")

            assertEquals("/api/files/download?path=%2Fx.bin", pathAndQuery)
            assertEquals("binary-not-json-payload", response.body()?.decodeToString())
        }

    @Test
    fun uploadManagedFile_postsBodyAndDecodes() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            var body: String? = null
            val engine =
                jsonResponder("""{"ok":true,"path":"/x"}""") { p, m, b ->
                    pathAndQuery = p
                    method = m
                    body = b
                }

            val response =
                api(engine).uploadManagedFile(
                    com.m57.hermescontrol.data.model.ManagedFileUpload(
                        path = "/x",
                        data_url = "data:text/plain,hi",
                    ),
                )

            assertEquals("/api/files/upload", pathAndQuery)
            assertEquals("POST", method)
            assertEquals("""{"path":"/x","data_url":"data:text/plain,hi"}""", body)
            assertEquals(true, response.body()?.ok)
        }

    @Test
    fun createManagedDirectory_postsAndDecodes() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            var body: String? = null
            val engine =
                jsonResponder("""{"ok":true,"path":"/d"}""") { p, m, b ->
                    pathAndQuery = p
                    method = m
                    body = b
                }

            val response =
                api(engine).createManagedDirectory(
                    com.m57.hermescontrol.data.model.ManagedDirectoryCreate(path = "/d"),
                )

            assertEquals("/api/files/mkdir", pathAndQuery)
            assertEquals("POST", method)
            assertEquals("""{"path":"/d"}""", body)
            assertEquals(true, response.body()?.ok)
        }

    @Test
    fun deleteManagedFile_sendsDeleteWithBodyAndDecodes() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            var body: String? = null
            val engine =
                jsonResponder("""{"ok":true,"path":"/x"}""") { p, m, b ->
                    pathAndQuery = p
                    method = m
                    body = b
                }

            val response =
                api(engine).deleteManagedFile(
                    com.m57.hermescontrol.data.model.ManagedFileDelete(path = "/x"),
                )

            assertEquals("/api/files", pathAndQuery)
            assertEquals("DELETE", method)
            assertEquals("""{"path":"/x"}""", body)
            assertEquals(true, response.body()?.ok)
        }

    @Test
    fun uploadManagedFileStream_postsMultipartFormDataWithFields() =
        runTest {
            var pathAndQuery: String? = null
            var method: String? = null
            var contentType: String? = null
            var bodyText: String? = null
            val engine =
                MockEngine { request ->
                    pathAndQuery = request.url.encodedPath
                    method = request.method.value
                    contentType = request.body.contentType?.toString()
                    bodyText = request.body.toByteArray().decodeToString()
                    respond(
                        """{"ok":true}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }

            val response =
                api(engine).uploadManagedFileStream(
                    path = "docs/a.txt",
                    overwrite = true,
                    fileName = "a.txt",
                    content = "hello-upload".encodeToByteArray(),
                )

            assertEquals("/api/files/upload-stream", pathAndQuery)
            assertEquals("POST", method)
            assertTrue(contentType?.startsWith("multipart/form-data") == true, "was: $contentType")
            assertTrue(bodyText?.contains("name=\"path\"") == true)
            assertTrue(bodyText?.contains("docs/a.txt") == true)
            assertTrue(bodyText?.contains("name=\"overwrite\"") == true)
            assertTrue(bodyText?.contains("name=\"file\"") == true)
            assertTrue(bodyText?.contains("hello-upload") == true)
            assertEquals(true, response.body()?.ok)
        }
}
