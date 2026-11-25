package com.m57.hermescontrol.data.model
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SessionListResponse(
    val sessions: List<SessionInfo>,
    val total: Int = 0,
    val limit: Int = 0,
    val offset: Int = 0,
)

@Serializable
data class SessionInfo(
    val id: String,
    val title: String? = null,
    val created_at: String? = null,
    val message_count: Int? = null,
    val status: String? = null,
    val preview: String? = null,
    val started_at: Double? = null,
    /**
     * Unix seconds of the session's **last activity** as the gateway records it
     * (`sessions.last_activity_at`). REST exposes it; the `session.list` RPC does not,
     * which is why the rail reads REST. Null on rows that predate the heartbeat — callers
     * fall back to [started_at], a lower bound on the same value.
     */
    val last_active: Double? = null,
    val source: String? = null,
    val parent_session_id: String? = null,
    val display_name: String? = null,
    val model: String? = null,
    val _lineage_root_id: String? = null,
    val terminal_backend: String? = null,
    /** Optimistic-concurrency fence for session mutations (gateway `runtime_revision`). */
    @SerialName("runtime_revision")
    val runtimeRevision: Int? = null,
    /** Runtime the revision belongs to (gateway `runtime_generation`). */
    @SerialName("runtime_generation")
    val runtimeGeneration: Int? = null,
)

@Serializable
data class SessionStatsResponse(
    val total: Int = 0,
    val messages: Int = 0,
)

@Serializable
data class SessionLatestDescendantResponse(
    val session_id: String,
)

@Serializable
data class SessionRenameRequest(
    val title: String? = null,
    val archived: Boolean? = null,
    val hidden: Boolean? = null,
    val pinned: Boolean? = null,
    val unread: Boolean? = null,
    val profile: String? = null,
    /**
     * Mutation fence. The gateway refuses an unfenced write with
     * 409 `client_upgrade_required` (web_server_sessions.py:286), and rejects a stale
     * revision as a conflict, so a write can never land on state the client did not see.
     */
    @SerialName("request_id") val requestId: String? = null,
    @SerialName("expected_revision") val expectedRevision: Int? = null,
    @SerialName("expected_generation") val expectedGeneration: Int? = null,
)

@Serializable
data class BulkDeleteRequest(
    val ids: List<String>,
    @SerialName("delete_all") val deleteAll: Boolean = false,
    /** Profile owning the rows; omitted on a multi-profile host the gateway answers 400. */
    val profile: String? = null,
)

@Serializable
data class BulkDeleteResponse(
    val ok: Boolean = false,
    val deleted: Int = 0,
)

@Serializable
data class PruneRequest(
    /** Gateway field is `older_than_days`; `days` was a client-side invention. */
    @SerialName("older_than_days") val olderThanDays: Int,
    val profile: String? = null,
)
