package com.m57.hermescontrol.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ToolSchemaRegistryTest {
    @Test
    fun `tool configs use material symbol names`() {
        // Icons are addressed by Material Symbols name rather than by ImageVector,
        // because the shared UI renders them from a variable font on every target.
        assertEquals(
            "terminal",
            ToolSchemaRegistry.getDisplayConfig("terminal").icon,
        )
        assertEquals(
            "description",
            ToolSchemaRegistry.getDisplayConfig("read_file").icon,
        )
    }

    @Test
    fun `only terminal keeps a textual summary prefix`() {
        assertEquals(
            "$ ",
            ToolSchemaRegistry.getDisplayConfig("terminal").summaryPrefix,
        )
        assertEquals(
            "",
            ToolSchemaRegistry.getDisplayConfig("read_file").summaryPrefix,
        )
        assertEquals(
            "",
            ToolSchemaRegistry.getDisplayConfig("web_search").summaryPrefix,
        )
    }
}
