package com.m57.hermescontrol.ui.sessions

/** History is split by provenance: interactive conversations vs. scheduled automation runs. */
enum class HistorySection {
    CONVERSATIONS,
    AUTOMATIONS,
}

val HistorySection.showsGlobalStats: Boolean
    get() = this == HistorySection.CONVERSATIONS

val HistorySection.source: String?
    get() = if (this == HistorySection.AUTOMATIONS) AUTOMATION_SOURCE else null

val HistorySection.excludeSources: String?
    get() = if (this == HistorySection.CONVERSATIONS) AUTOMATION_SOURCE else null
