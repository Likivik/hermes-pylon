package com.m57.hermescontrol.ui.chat

import com.m57.hermescontrol.data.model.SessionInfo
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

    // ── REST rows: the rail's real source ─────────────────────────────────

    private fun info(
        id: String,
        title: String? = "t",
        startedAt: Double? = 1_700_000_000.0,
        lastActive: Double? = null,
        source: String? = null,
    ): SessionInfo =
        SessionInfo(
            id = id,
            title = title,
            started_at = startedAt,
            last_active = lastActive,
            source = source,
            message_count = 1,
        )

    @Test
    fun `REST last_active is the fold axis, not the creation time`() {
        val row = info("s1", startedAt = 1_700_000_000.0, lastActive = 1_760_000_000.0)
        assertEquals(1_760_000_000L, railSessionOf(row)?.lastActive)
    }

    @Test
    fun `a row without last_active falls back to started_at`() {
        val row = info("s1", startedAt = 1_700_000_000.0, lastActive = null)
        assertEquals(1_700_000_000L, railSessionOf(row)?.lastActive)
    }

    @Test
    fun `REST rows keep the server's last-activity order`() {
        val rows =
            listOf(
                info("recent", startedAt = 1_600_000_000.0, lastActive = 1_760_000_000.0),
                info("older", startedAt = 1_750_000_000.0, lastActive = 1_750_100_000.0),
            )
        // The first row was created earlier but used later; sorting on started_at would
        // invert this, which is the bug this change exists to fix.
        assertEquals(listOf("recent", "older"), railSessionsOf(rows).map { it.id })
    }

    @Test
    fun `REST rows go through the same cron filter`() {
        val rows = listOf(info("cron_j_20261117_040000", source = "cron"), info("s1"), info("cron_k_20261117_050000"))
        assertEquals(listOf("s1"), railSessionsOf(rows).map { it.id })
    }

    @Test
    fun `a blank REST title falls back instead of rendering empty`() {
        assertEquals("Untitled", railSessionOf(info("s1", title = "  "))?.title)
        assertEquals("Untitled", railSessionOf(info("s1", title = null))?.title)
    }
}
