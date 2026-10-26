package com.m57.hermescontrol.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LogRedactionTest {
    @Test
    fun `url drops query and fragment`() {
        assertEquals(
            "https://gw.test/api/ws",
            LogRedaction.url("https://gw.test/api/ws?ticket=abc123&token=zzz#frag"),
        )
    }

    @Test
    fun `url strips userinfo`() {
        assertEquals(
            "https://gw.test/api/status",
            LogRedaction.url("https://user:pass@gw.test/api/status?x=1"),
        )
    }

    @Test
    fun `secret headers are masked and unknown headers dropped`() {
        assertEquals("<redacted len=7>", LogRedaction.header("Authorization", "Bearer "))
        assertEquals("HermesWebUI", LogRedaction.header("Server", "HermesWebUI"))
        assertNull(LogRedaction.header("X-Weird", "value"))
    }

    @Test
    fun `mask distinguishes absent from empty`() {
        assertEquals("<absent>", LogRedaction.mask(null))
        assertEquals("<empty>", LogRedaction.mask(""))
        assertEquals("<redacted len=3>", LogRedaction.mask("abc"))
    }

    @Test
    fun `attributes cap count and value length`() {
        val big = (1..40).associate { "k$it" to "v".repeat(500) }
        val redacted = LogRedaction.attributes(big)

        assertEquals(12, redacted.size)
        assertTrue(redacted.values.all { it.length <= 200 })
    }
}
