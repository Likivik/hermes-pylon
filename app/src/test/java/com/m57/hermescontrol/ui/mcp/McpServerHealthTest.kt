package com.m57.hermescontrol.ui.mcp

import com.m57.hermescontrol.data.model.McpServerTestResponse
import com.m57.hermescontrol.data.model.McpServerToolInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class McpServerHealthTest {
    @Test
    fun `token estimate rounds each schema up before summing`() {
        val tools =
            listOf(
                McpServerToolInfo(name = "read", schemaChars = 400),
                McpServerToolInfo(name = "write", schemaChars = 401),
                McpServerToolInfo(name = "unknown"),
            )

        assertEquals(201, McpTokenEstimator.estimateTokens(tools))
        assertEquals("3 tools • ~201 tokens", McpTokenEstimator.formatTokenOverhead(3, 201))
        assertEquals("49 tools • ~3.2k tokens", McpTokenEstimator.formatTokenOverhead(49, 3200))
    }

    @Test
    fun `token estimate is absent when schemas report no positive size`() {
        val tools =
            listOf(
                McpServerToolInfo(name = "missing"),
                McpServerToolInfo(name = "empty", schemaChars = 0),
            )

        assertNull(McpTokenEstimator.estimateTokens(tools))
        assertEquals("2 tools", McpTokenEstimator.formatTokenOverhead(2, null))
    }

    @Test
    fun `health favors active tests and recognizes authentication failures`() {
        assertEquals(
            McpHealthStatus.TESTING,
            McpHealthStatus.resolve(true, McpServerTestResponse(ok = true), "running", null),
        )
        assertEquals(
            McpHealthStatus.AUTH_REQUIRED,
            McpHealthStatus.resolve(
                false,
                McpServerTestResponse(ok = false, error = "OAuth token required"),
                null,
                null,
            ),
        )
        assertEquals(
            McpHealthStatus.HEALTHY,
            McpHealthStatus.resolve(false, McpServerTestResponse(ok = true), null, null),
        )
        assertEquals(
            McpHealthStatus.ERROR,
            McpHealthStatus.resolve(false, null, null, "Process exited with 1"),
        )
    }
}
