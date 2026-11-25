package com.m57.hermescontrol.ui.chat

import com.m57.hermescontrol.data.model.SessionInfo
import com.m57.hermescontrol.ui.sessions.AUTOMATION_SOURCE

/**
 * `cron.scheduler` mints a durable run id per tick: `cron_{job_id}_{YYYYMMDD_HHMMSS}`.
 * The timestamp suffix guarantees one session per run, so a job firing every twenty
 * minutes alone produces 36 a workday — which is why cron runs are kept out of the chat rail entirely and reachable
 * only from History → Automations.
 */
private val CRON_RUN_ID = Regex("^cron_.+_[0-9]{8}_[0-9]{6}$")

/**
 * True when a session is a cron run. Prefers the server's own `source`; falls back to the
 * documented run-id shape for payloads that omit it. Never infer this from a title.
 */
val SessionUi.isAutomation: Boolean
    get() = source == AUTOMATION_SOURCE || CRON_RUN_ID.matches(id)

/** Maps one `session.list` entry. Returns null for entries with no usable id. */
fun railSessionOf(raw: Map<String, Any?>): SessionUi? {
    val id = raw["id"] as? String ?: return null
    if (id.isEmpty()) return null
    return SessionUi(
        id = id,
        title = raw["title"] as? String ?: "Untitled",
        messageCount = (raw["message_count"] as? Double)?.toInt() ?: 0,
        lastActive = (raw["last_active"] as? Double ?: raw["started_at"] as? Double)?.toLong() ?: 0L,
        source = raw["source"] as? String,
    )
}

/**
 * The rail's view of a `session.list` reply: every conversation, no cron runs.
 *
 * Dropping them here rather than in the UI keeps one rule in one place: the rail, the
 * keyboard switcher and anything else reading `sessions` all see the same list.
 */
fun railSessions(raw: List<Map<String, Any?>>): List<SessionUi> =
    raw.mapNotNull(::railSessionOf).filterNot { it.isAutomation }

/**
 * Maps one `GET /api/sessions` row — the rail's real source.
 *
 * REST carries `last_active`, the gateway's own activity timestamp, which the
 * `session.list` RPC drops. `started_at` is a lower bound on the same value, so it is
 * the fallback for rows that predate the heartbeat — never the primary.
 */
fun railSessionOf(info: SessionInfo): SessionUi? {
    if (info.id.isEmpty()) return null
    return SessionUi(
        id = info.id,
        title = info.title?.takeIf { it.isNotBlank() } ?: "Untitled",
        messageCount = info.message_count ?: 0,
        lastActive = (info.last_active ?: info.started_at)?.toLong() ?: 0L,
        source = info.source,
    )
}

/** [railSessions] over decoded REST rows: same cron rule, same order as the server sent. */
fun railSessionsOf(rows: List<SessionInfo>): List<SessionUi> =
    rows.mapNotNull(::railSessionOf).filterNot { it.isAutomation }
