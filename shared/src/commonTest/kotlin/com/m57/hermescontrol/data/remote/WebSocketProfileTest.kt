package com.m57.hermescontrol.data.remote

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The socket is scoped at handshake (`chat_ws.py:249,499`): a profile missing from the URL
 * silently connects to the process's own profile.
 */
class WebSocketProfileTest {
    private val endpoint =
        ServerEndpoint.parse(
            rawValue = "https://gateway.test/",
            cleartextPolicy = CleartextPolicy.ALLOW_WITH_WARNING,
        )

    @Test
    fun profileRidesOnTheHandshakeUrl() {
        val url = endpoint.webSocketUrl(authParameter = "token", credential = "abc", profile = "work")

        assertTrue("token=abc" in url, url)
        assertTrue("&profile=work" in url, url)
    }

    @Test
    fun absentProfileLeavesTheUrlUnscoped() {
        val url = endpoint.webSocketUrl(authParameter = "token", credential = "abc", profile = null)

        assertFalse("profile=" in url, url)
    }

    @Test
    fun blankProfileIsTreatedAsAbsent() {
        val url = endpoint.webSocketUrl(authParameter = "ticket", credential = "t", profile = "  ")

        assertFalse("profile=" in url, url)
    }
}
