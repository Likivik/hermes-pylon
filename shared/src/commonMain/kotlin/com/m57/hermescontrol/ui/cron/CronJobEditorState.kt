package com.m57.hermescontrol.ui.cron

/**
 * Form state for the cron-job editor.
 *
 * Pure data — no Compose, no Android, nothing platform-shaped — so it lives in
 * `commonMain` and can be exercised on every target. `:app` keeps compiling
 * against it unchanged because this is the same package it already used.
 */
data class CronJobEditorState(
    val isOpen: Boolean = false,
    val isNew: Boolean = false,
    val jobId: String? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val toastMessage: String? = null,
    // Form fields
    val name: String = "",
    val schedule: String = "",
    val prompt: String = "",
    val deliver: String = "local",
    val skills: String = "",
    val model: String = "",
    val provider: String = "",
    val base_url: String = "",
    val script: String = "",
    val workdir: String = "",
    val enabled: Boolean = true,
    val no_agent: Boolean = false,
    val runContinuity: Boolean = false,
    val contextFrom: List<String> = emptyList(),
    val monitorMode: String = "off",
    val monitor_script: String = "",
    val monitor_url: String = "",
)

/**
 * Marker inside `context_from` meaning "continue from this same job".
 *
 * Public because callers both build and inspect `contextFrom`; keeping it here
 * makes it single-sourced instead of duplicated per call site.
 */
const val SELF_CONTEXT_SOURCE = "self"

/**
 * Applies one form-field edit, keyed by the backend's field name.
 *
 * Note the two monitor fields are mutually exclusive: filling one clears the
 * other, because the gateway accepts only one monitor source per job. An
 * unrecognised field is a no-op rather than an error, so an unknown key from
 * the server cannot corrupt the form.
 */
fun CronJobEditorState.applyFieldChange(
    name: String,
    value: String,
): CronJobEditorState =
    when (name) {
        "name" -> copy(name = value)
        "schedule" -> copy(schedule = value)
        "prompt" -> copy(prompt = value)
        "deliver" -> copy(deliver = value)
        "skills" -> copy(skills = value)
        "model" -> copy(model = value)
        "provider" -> copy(provider = value)
        "base_url" -> copy(base_url = value)
        "script" -> copy(script = value)
        "workdir" -> copy(workdir = value)
        "monitor_script" ->
            copy(
                monitor_script = value,
                monitor_url = if (value.isNotBlank()) "" else monitor_url,
            )
        "monitor_url" ->
            copy(
                monitor_url = value,
                monitor_script = if (value.isNotBlank()) "" else monitor_script,
            )
        else -> this
    }

/** Monitor fields actually worth sending: present and trimmed, never blank. */
fun editorMonitorUpdates(editor: CronJobEditorState): Map<String, String> =
    buildMap {
        if (editor.monitor_script.isNotBlank()) put("monitor_script", editor.monitor_script.trim())
        if (editor.monitor_url.isNotBlank()) put("monitor_url", editor.monitor_url.trim())
    }

/**
 * `context_from` as the gateway should receive it: [SELF_CONTEXT_SOURCE] present
 * exactly when continuity is on, absent when it is off.
 */
fun CronJobEditorState.continuityContextSources(): List<String> {
    if (!runContinuity) {
        return contextFrom.filterNot { it == SELF_CONTEXT_SOURCE }
    }
    if (SELF_CONTEXT_SOURCE in contextFrom) {
        return contextFrom
    }
    return contextFrom + SELF_CONTEXT_SOURCE
}
