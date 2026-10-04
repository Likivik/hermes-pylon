package com.m57.hermescontrol.ui.chat.components

import com.m57.hermescontrol.ui.chat.ChatMessage
import com.m57.hermescontrol.ui.chat.MessageRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ChatMessageContentTypeTest {
    @Test
    fun `different message roles use distinct reusable layouts`() {
        val types =
            MessageRole.entries.map { role ->
                chatMessageContentType(ChatMessage(role = role, content = "text"))
            }
        assertEquals(MessageRole.entries.size, types.toSet().size)
    }

    @Test
    fun `content and streaming updates retain the layout type`() {
        val message = ChatMessage(role = MessageRole.ASSISTANT, content = "first")
        assertEquals(
            chatMessageContentType(message),
            chatMessageContentType(message.copy(content = "second", isStreaming = true)),
        )
    }

    @Test
    fun `timeline notices do not reuse ordinary message layouts`() {
        val message = ChatMessage(role = MessageRole.ASSISTANT, content = "notice")
        assertEquals("system_event", chatMessageContentType(message.copy(displayKind = "notice")))
        assertNotEquals(
            chatMessageContentType(message),
            chatMessageContentType(message.copy(displayKind = "notice")),
        )
    }
}
