// Modified from Hy4ri/hermes-mobile for this fork; see NOTICE.

package com.m57.hermescontrol.ui.sessions

import com.m57.hermescontrol.data.model.SessionInfo

/** Server `source` value that marks a session as an automation (cron) run. */
internal const val AUTOMATION_SOURCE = "cron"

internal data class AutomationSessionGroup(
    val key: String,
    val jobId: String?,
    val sessions: List<SessionInfo>,
    val title: String? = null,
)

// cron.scheduler mints durable run IDs as cron_{job_id}_{YYYYMMDD_HHMMSS}.
// Never infer job identity from a title: different jobs may have the same name.
private val CRON_RUN_ID = Regex("^cron_(.+)_[0-9]{8}_[0-9]{6}$")

internal fun SessionInfo.isAutomation(): Boolean = source == AUTOMATION_SOURCE

/** True when a row belongs to [section]; the server filters pages, pins are hydrated locally. */
internal fun SessionInfo.inSection(section: HistorySection): Boolean =
    isAutomation() == (section == HistorySection.AUTOMATIONS)

/**
 * Groups automation runs by the durable job identity encoded in their run ID. A run whose
 * ID carries no job identity keeps its own group so it stays independently reachable.
 */
internal fun automationGroups(sessions: List<SessionInfo>): List<AutomationSessionGroup> =
    sessions
        .distinctBy { it.id }
        .groupBy { session ->
            val jobId =
                if (session.isAutomation()) CRON_RUN_ID.matchEntire(session.id)?.groupValues?.get(1) else null
            jobId?.let { "job:$it" } ?: "session:${session.id}"
        }.map { (key, runs) ->
            AutomationSessionGroup(
                key = key,
                jobId = key.takeIf { it.startsWith("job:") }?.removePrefix("job:"),
                sessions = runs,
                title =
                    runs
                        .mapNotNull { it.title?.takeIf(String::isNotBlank) }
                        .firstOrNull()
                        ?.substringBeforeLast(" · ")
                        ?.trim()
                        ?.takeIf(String::isNotBlank),
            )
        }
