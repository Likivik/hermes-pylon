package com.m57.hermescontrol.data.remote.gateway

import com.m57.hermescontrol.data.model.LogResponse
import com.m57.hermescontrol.data.model.ManagedDirectoryCreate
import com.m57.hermescontrol.data.model.ManagedFileActionResponse
import com.m57.hermescontrol.data.model.ManagedFileDelete
import com.m57.hermescontrol.data.model.ManagedFilesListResponse
import com.m57.hermescontrol.data.model.PruneRequest
import com.m57.hermescontrol.data.model.SessionListResponse
import com.m57.hermescontrol.data.model.SessionStatsResponse
import com.m57.hermescontrol.data.remote.GatewayResponse
import com.m57.hermescontrol.data.remote.HermesGatewayApi

/**
 * Sessions, managed files and logs — what the rail, the files screen and the log viewer need.
 *
 * This is the first of four domain gateways. The rule they follow:
 *
 * **the transport stays one, the interfaces stay narrow.** `ApiClient` still builds the single HTTP client and
 * owns profile scoping; `HermesGatewayApi` is still the one place that knows route strings. A gateway does not
 * wrap the transport — it names the subset a screen depends on, so a screen's constructor says out loud what it
 * can do.
 *
 * Before this, 21 ViewModels reached into the global `ApiClient.hermesApi`. That is untestable (no fake can be
 * put in its place), invisible in a constructor, and it hides who needs what.
 */
interface SessionsGateway {
    suspend fun getSessions(
        limit: Int = 20,
        offset: Int = 0,
        order: String = "recent",
        source: String? = null,
        excludeSources: String? = null,
        profile: String? = null,
    ): GatewayResponse<SessionListResponse>

    suspend fun getSessionStats(): GatewayResponse<SessionStatsResponse>

    suspend fun pruneSessions(body: PruneRequest): GatewayResponse<Unit>

    suspend fun listManagedFiles(path: String? = null): GatewayResponse<ManagedFilesListResponse>

    suspend fun createManagedDirectory(body: ManagedDirectoryCreate): GatewayResponse<ManagedFileActionResponse>

    suspend fun deleteManagedFile(body: ManagedFileDelete): GatewayResponse<ManagedFileActionResponse>

    suspend fun uploadManagedFileStream(
        path: String,
        overwrite: Boolean,
        fileName: String,
        content: ByteArray,
        mimeType: String? = null,
    ): GatewayResponse<ManagedFileActionResponse>

    suspend fun getLogs(
        file: String = "agent",
        lines: Int = 100,
        level: String = "ALL",
        component: String = "all",
    ): GatewayResponse<LogResponse>
}

/** The production implementation: pure delegation, no logic of its own. */
class GatewaySessions(
    private val api: HermesGatewayApi,
) : SessionsGateway {
    override suspend fun getSessions(
        limit: Int,
        offset: Int,
        order: String,
        source: String?,
        excludeSources: String?,
        profile: String?,
    ): GatewayResponse<SessionListResponse> =
        api.getSessions(
            limit = limit,
            offset = offset,
            order = order,
            source = source,
            excludeSources = excludeSources,
            profile = profile,
        )

    override suspend fun getSessionStats(): GatewayResponse<SessionStatsResponse> = api.getSessionStats()

    override suspend fun pruneSessions(body: PruneRequest): GatewayResponse<Unit> = api.pruneSessions(body)

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
        api.uploadManagedFileStream(
            path = path,
            overwrite = overwrite,
            fileName = fileName,
            content = content,
            mimeType = mimeType,
        )

    override suspend fun getLogs(
        file: String,
        lines: Int,
        level: String,
        component: String,
    ): GatewayResponse<LogResponse> = api.getLogs(file = file, lines = lines, level = level, component = component)
}
