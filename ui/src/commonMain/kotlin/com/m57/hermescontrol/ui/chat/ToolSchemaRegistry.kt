package com.m57.hermescontrol.ui.chat

/**
 * Display configuration for a Hermes tool — controls the summary line,
 * icon, and which arg field is shown when the bubble is collapsed.
 *
 * Based on the canonical tool schemas defined in the Hermes Agent source
 * at /opt/hermes-agent/tools/ (each tool has a `*_SCHEMA` dict).
 */
data class ToolDisplayConfig(
    val name: String,
    /** Key into the `args` dict for the one-line summary (e.g. "command" for terminal). */
    val summaryArgKey: String? = null,
    /** Prefix for the summary line (e.g. "$ " for terminal). Emoji-free. */
    val summaryPrefix: String = "",
    /** Material icon for this tool type. */
    val icon: String = "build",
)

/** Maps tool names to their display config. Ordered by expected frequency of use. */
object ToolSchemaRegistry {
    private val knownTools: Map<String, ToolDisplayConfig> =
        mapOf(
            "terminal" to
                ToolDisplayConfig(
                    name = "terminal",
                    summaryArgKey = "command",
                    summaryPrefix = "$ ",
                    icon = "terminal",
                ),
            "read_file" to
                ToolDisplayConfig(
                    name = "read_file",
                    summaryArgKey = "path",
                    icon = "description",
                ),
            "write_file" to
                ToolDisplayConfig(
                    name = "write_file",
                    summaryArgKey = "path",
                    icon = "edit",
                ),
            "patch" to
                ToolDisplayConfig(
                    name = "patch",
                    summaryArgKey = "path",
                    icon = "build",
                ),
            "search_files" to
                ToolDisplayConfig(
                    name = "search_files",
                    summaryArgKey = "pattern",
                    icon = "search",
                ),
            "web_search" to
                ToolDisplayConfig(
                    name = "web_search",
                    summaryArgKey = "query",
                    icon = "public",
                ),
            "browser_navigate" to
                ToolDisplayConfig(
                    name = "browser_navigate",
                    summaryArgKey = "url",
                    icon = "language",
                ),
            "browser_click" to
                ToolDisplayConfig(
                    name = "browser_click",
                    summaryArgKey = "ref",
                    icon = "touch_app",
                ),
            "browser_snapshot" to
                ToolDisplayConfig(
                    name = "browser_snapshot",
                    summaryArgKey = null,
                    icon = "photo",
                ),
            "clarify" to
                ToolDisplayConfig(
                    name = "clarify",
                    summaryArgKey = "question",
                    icon = "chat_bubble",
                ),
            "delegate_task" to
                ToolDisplayConfig(
                    name = "delegate_task",
                    summaryArgKey = "goal",
                    icon = "account_tree",
                ),
            "execute_code" to
                ToolDisplayConfig(
                    name = "execute_code",
                    summaryArgKey = "code",
                    icon = "play_arrow",
                ),
            "todo" to
                ToolDisplayConfig(
                    name = "todo",
                    summaryArgKey = null,
                    icon = "checklist",
                ),
            "fact_store" to
                ToolDisplayConfig(
                    name = "fact_store",
                    summaryArgKey = null,
                    icon = "psychology",
                ),
            "session_search" to
                ToolDisplayConfig(
                    name = "session_search",
                    summaryArgKey = null,
                    icon = "search",
                ),
            // ── Action-based tools ──
            "cronjob" to
                ToolDisplayConfig(
                    name = "cronjob",
                    summaryArgKey = "action",
                    icon = "schedule",
                ),
            "memory" to
                ToolDisplayConfig(
                    name = "memory",
                    summaryArgKey = "action",
                    icon = "memory",
                ),
            "fact_feedback" to
                ToolDisplayConfig(
                    name = "fact_feedback",
                    summaryArgKey = "action",
                    icon = "thumb_up",
                ),
            "process" to
                ToolDisplayConfig(
                    name = "process",
                    summaryArgKey = "action",
                    icon = "settings",
                ),
            "skill_manage" to
                ToolDisplayConfig(
                    name = "skill_manage",
                    summaryArgKey = "action",
                    icon = "build",
                ),
            // ── Web / Browser tools ──
            "web_extract" to
                ToolDisplayConfig(
                    name = "web_extract",
                    summaryArgKey = "urls",
                    icon = "language",
                ),
            "browser_type" to
                ToolDisplayConfig(
                    name = "browser_type",
                    summaryArgKey = null,
                    icon = "keyboard",
                ),
            "browser_cdp" to
                ToolDisplayConfig(
                    name = "browser_cdp",
                    summaryArgKey = "action",
                    icon = "build",
                ),
            "browser_dialog" to
                ToolDisplayConfig(
                    name = "browser_dialog",
                    summaryArgKey = "action",
                    icon = "chat_bubble",
                ),
            // ── Media / Vision tools ──
            "vision_analyze" to
                ToolDisplayConfig(
                    name = "vision_analyze",
                    summaryArgKey = "image_url",
                    icon = "visibility",
                ),
            "text_to_speech" to
                ToolDisplayConfig(
                    name = "text_to_speech",
                    summaryArgKey = null,
                    icon = "volume_up",
                ),
            "video_generate" to
                ToolDisplayConfig(
                    name = "video_generate",
                    summaryArgKey = null,
                    icon = "movie",
                ),
            "image_generate" to
                ToolDisplayConfig(
                    name = "image_generate",
                    summaryArgKey = null,
                    icon = "image",
                ),
            // ── Social / Messaging tools ──
            "x_search" to
                ToolDisplayConfig(
                    name = "x_search",
                    summaryArgKey = "query",
                    icon = "forum",
                ),
            "send_message" to
                ToolDisplayConfig(
                    name = "send_message",
                    summaryArgKey = null,
                    icon = "send",
                ),
            // ── Skills tools ──
            "skills_list" to
                ToolDisplayConfig(
                    name = "skills_list",
                    summaryArgKey = null,
                    icon = "menu_book",
                ),
            "skill_view" to
                ToolDisplayConfig(
                    name = "skill_view",
                    summaryArgKey = "name",
                    icon = "auto_stories",
                ),
            // ── On-demand tools ──
            "tool_search" to
                ToolDisplayConfig(
                    name = "tool_search",
                    summaryArgKey = "query",
                    icon = "search",
                ),
            "tool_describe" to
                ToolDisplayConfig(
                    name = "tool_describe",
                    summaryArgKey = "name",
                    icon = "auto_stories",
                ),
            "tool_call" to
                ToolDisplayConfig(
                    name = "tool_call",
                    summaryArgKey = "name",
                    icon = "build",
                ),
            // ── Misc ──
            "read_terminal" to
                ToolDisplayConfig(
                    name = "read_terminal",
                    summaryArgKey = "session_id",
                    icon = "computer",
                ),
            "computer_use" to
                ToolDisplayConfig(
                    name = "computer_use",
                    summaryArgKey = "action",
                    icon = "computer",
                ),
        )

    fun getDisplayConfig(toolName: String?): ToolDisplayConfig =
        toolName?.let { knownTools[it] } ?: ToolDisplayConfig(name = toolName ?: "tool")
}
