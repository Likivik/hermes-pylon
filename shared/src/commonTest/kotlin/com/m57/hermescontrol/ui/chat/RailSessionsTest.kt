package com.m57.hermescontrol.ui.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Cron runs are one session per tick; the rail must never show them. */
class RailSessionsTest {
    private fun raw(
        id: String,
        title: String = "t",
        source: String? = null,
    ): Map<String, Any?> =
        buildMap {
            put("id", id)
            put("title", title)
            put("message_count", 1.0)
            put("started_at", 1_700_000_000.0)
            if (source != null) put("source", source)
        }

    @Test
    fun `a cron run is dropped by its source`() {
        val out = railSessions(listOf(raw("cron_abc_20261117_040000", source = "cron"), raw("s1")))
        assertEquals(listOf("s1"), out.map { it.id })
    }

    @Test
    fun `a cron run is dropped by its id shape when the payload has no source`() {
        val out = railSessions(listOf(raw("cron_9f2_20261117_040000"), raw("s1")))
        assertEquals(listOf("s1"), out.map { it.id })
    }

    @Test
    fun `a conversation that merely mentions cron is kept`() {
        val out = railSessions(listOf(raw("s1", title = "why do cron_ jobs multiply", source = "desktop")))
        assertEquals(listOf("s1"), out.map { it.id })
    }

    @Test
    fun `a cron-looking title is not enough to hide a real conversation`() {
        val out = railSessions(listOf(raw("s1", title = "cron_daily_20260101_000000")))
        assertEquals(listOf("s1"), out.map { it.id }, "identity comes from source or id, never a title")
    }

    @Test
    fun `entries without an id are skipped rather than rendered blank`() {
        assertNull(railSessionOf(mapOf("title" to "no id")))
        assertEquals(0, railSessions(listOf(mapOf("id" to ""))).size)
    }

    @Test
    fun `source is carried through and the flag agrees`() {
        val session = railSessionOf(raw("s1", source = "desktop"))
        assertEquals("desktop", session?.source)
        assertFalse(session!!.isAutomation)
        assertTrue(railSessionOf(raw("cron_x_20261117_040000", source = "cron"))!!.isAutomation)
    }
}
