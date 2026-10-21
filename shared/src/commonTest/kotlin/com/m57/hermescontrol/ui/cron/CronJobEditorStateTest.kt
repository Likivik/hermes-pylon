package com.m57.hermescontrol.ui.cron

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The cron editor's form rules, which used to be private helpers inside
 * `CronJobsViewModel`. They run on every target now, wasmJs included.
 */
class CronJobEditorStateTest {
    @Test
    fun `field edits are keyed by the backend's field names`() {
        val edited =
            CronJobEditorState()
                .applyFieldChange("name", "nightly")
                .applyFieldChange("schedule", "0 3 * * *")
                .applyFieldChange("prompt", "tidy up")
                .applyFieldChange("deliver", "telegram")
                .applyFieldChange("skills", "a,b")
                .applyFieldChange("model", "m1")
                .applyFieldChange("provider", "p1")
                .applyFieldChange("base_url", "http://x")
                .applyFieldChange("script", "echo hi")
                .applyFieldChange("workdir", "/tmp")
        assertEquals("nightly", edited.name)
        assertEquals("0 3 * * *", edited.schedule)
        assertEquals("tidy up", edited.prompt)
        assertEquals("telegram", edited.deliver)
        assertEquals("a,b", edited.skills)
        assertEquals("m1", edited.model)
        assertEquals("p1", edited.provider)
        assertEquals("http://x", edited.base_url)
        assertEquals("echo hi", edited.script)
        assertEquals("/tmp", edited.workdir)
    }

    @Test
    fun `an unrecognised field is a no-op`() {
        val before = CronJobEditorState(name = "keep", schedule = "1 1 1 1 1")
        assertEquals(before, before.applyFieldChange("totally_unknown", "boom"))
    }

    @Test
    fun `filling monitor_script clears monitor_url`() {
        val edited =
            CronJobEditorState(monitor_url = "http://old")
                .applyFieldChange("monitor_script", "check.sh")
        assertEquals("check.sh", edited.monitor_script)
        assertEquals("", edited.monitor_url)
    }

    @Test
    fun `filling monitor_url clears monitor_script`() {
        val edited =
            CronJobEditorState(monitor_script = "check.sh")
                .applyFieldChange("monitor_url", "http://new")
        assertEquals("http://new", edited.monitor_url)
        assertEquals("", edited.monitor_script)
    }

    @Test
    fun `clearing a monitor field leaves the other one alone`() {
        val edited =
            CronJobEditorState(monitor_url = "http://keep")
                .applyFieldChange("monitor_script", "")
        assertEquals("", edited.monitor_script)
        assertEquals("http://keep", edited.monitor_url)
    }

    @Test
    fun `monitor updates omit blanks and trim values`() {
        assertEquals(
            mapOf("monitor_script" to "check.sh"),
            editorMonitorUpdates(CronJobEditorState(monitor_script = "  check.sh  ")),
        )
        assertEquals(
            mapOf("monitor_url" to "http://x"),
            editorMonitorUpdates(CronJobEditorState(monitor_url = "http://x")),
        )
        assertTrue(editorMonitorUpdates(CronJobEditorState()).isEmpty())
        assertTrue(editorMonitorUpdates(CronJobEditorState(monitor_script = "   ")).isEmpty())
    }

    @Test
    fun `continuity adds the self source when it is on`() {
        val sources =
            CronJobEditorState(runContinuity = true, contextFrom = listOf("a"))
                .continuityContextSources()
        assertEquals(listOf("a", SELF_CONTEXT_SOURCE), sources)
    }

    @Test
    fun `continuity does not duplicate an existing self source`() {
        val sources =
            CronJobEditorState(runContinuity = true, contextFrom = listOf("a", SELF_CONTEXT_SOURCE))
                .continuityContextSources()
        assertEquals(listOf("a", SELF_CONTEXT_SOURCE), sources)
    }

    @Test
    fun `turning continuity off strips the self source`() {
        val sources =
            CronJobEditorState(runContinuity = false, contextFrom = listOf("a", SELF_CONTEXT_SOURCE))
                .continuityContextSources()
        assertEquals(listOf("a"), sources)
        assertFalse(SELF_CONTEXT_SOURCE in sources)
    }

    @Test
    fun `continuity off with no self source is unchanged`() {
        val sources =
            CronJobEditorState(runContinuity = false, contextFrom = listOf("a", "b"))
                .continuityContextSources()
        assertEquals(listOf("a", "b"), sources)
    }
}
