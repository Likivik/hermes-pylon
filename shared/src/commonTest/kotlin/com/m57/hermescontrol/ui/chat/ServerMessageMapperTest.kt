package com.m57.hermescontrol.ui.chat

import com.m57.hermescontrol.data.model.AttachmentSource
import com.m57.hermescontrol.data.model.SessionMessage
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the server→UI message mapping that used to live in `ChatViewModel`.
 * It runs on every target, wasmJs included, which is the point of moving it.
 */
class ServerMessageMapperTest {
    private fun map(
        messages: List<SessionMessage>,
        existing: List<ChatMessage> = emptyList(),
        offset: Int = 0,
        useLatestPaging: Boolean = false,
        now: Long = 1_700_000_000_000L,
    ) = ServerMessageMapper.map(
        sessionId = "s1",
        messages = messages,
        existingMessages = existing,
        offset = offset,
        useLatestPaging = useLatestPaging,
        nowMillis = now,
    )

    @Test
    fun `maps roles and defaults unknown roles to assistant`() {
        val out =
            map(
                listOf(
                    SessionMessage(id = 1, role = "user", content = "hi"),
                    SessionMessage(id = 2, role = "system", content = "sys"),
                    SessionMessage(id = 3, role = "tool", content = "out", toolName = "grep", toolCallId = "c1"),
                    SessionMessage(id = 4, role = null, content = "who?"),
                ),
            )
        assertEquals(4, out.size)
        assertEquals(MessageRole.USER, out[0].role)
        assertEquals(MessageRole.SYSTEM, out[1].role)
        assertEquals(MessageRole.TOOL, out[2].role)
        assertEquals(MessageRole.ASSISTANT, out[3].role)
        assertEquals("grep", out[2].toolName)
        assertEquals("c1", out[2].toolCallId)
        assertEquals(ToolStatus.COMPLETED, out[2].toolStatus)
        assertNull(out[0].toolStatus)
    }

    @Test
    fun `ids come from the offset when not latest paging`() {
        val out = map(listOf(SessionMessage(id = 9, role = "user", content = "a")), offset = 5)
        assertEquals("rest-s1-5", out.single().id)
    }

    @Test
    fun `ids prefer the server id when latest paging`() {
        val out =
            map(
                listOf(SessionMessage(id = 9, role = "user", content = "a")),
                offset = 5,
                useLatestPaging = true,
            )
        assertEquals("rest-s1-9", out.single().id)
    }

    @Test
    fun `drops empty assistant placeholders but keeps empty user rows`() {
        val out =
            map(
                listOf(
                    SessionMessage(id = 1, role = "assistant", content = ""),
                    SessionMessage(id = 2, role = "user", content = ""),
                ),
            )
        assertEquals(1, out.size)
        assertEquals(MessageRole.USER, out.single().role)
    }

    @Test
    fun `keeps a reasoning-only assistant row`() {
        val out = map(listOf(SessionMessage(id = 1, role = "assistant", content = "", reasoning = "thinking")))
        assertEquals("thinking", out.single().reasoningText)
    }

    @Test
    fun `preserves reasoning from the displayed message with the same id`() {
        val existing =
            listOf(
                ChatMessage(
                    id = "rest-s1-0",
                    role = MessageRole.ASSISTANT,
                    content = "answer",
                    reasoningText = "because",
                ),
            )
        val out =
            map(
                listOf(SessionMessage(id = 1, role = "assistant", content = "answer")),
                existing = existing,
            )
        assertEquals("because", out.single().reasoningText)
    }

    @Test
    fun `does not carry reasoning across a content change`() {
        val existing =
            listOf(
                ChatMessage(
                    id = "rest-s1-0",
                    role = MessageRole.ASSISTANT,
                    content = "old",
                    reasoningText = "because",
                ),
            )
        val out =
            map(
                listOf(SessionMessage(id = 1, role = "assistant", content = "new")),
                existing = existing,
            )
        assertEquals("", out.single().reasoningText)
    }

    @Test
    fun `strips MEDIA and attaches gateway files on assistant rows`() {
        val out = map(listOf(SessionMessage(id = 1, role = "assistant", content = "Here: MEDIA:/tmp/x.png done")))
        val msg = out.single()
        val attachment = requireNotNull(msg.attachments).single()
        assertEquals("x.png", attachment.name)
        assertEquals("image/png", attachment.mimeType)
        assertEquals("/tmp/x.png", attachment.gatewayPath)
        assertEquals(AttachmentSource.GATEWAY, attachment.source)
        assertTrue(!msg.content.contains("MEDIA:"))
    }

    @Test
    fun `does not attach MEDIA on non-assistant rows`() {
        val out = map(listOf(SessionMessage(id = 1, role = "user", content = "MEDIA:/tmp/x.png")))
        assertNull(out.single().attachments)
    }

    @Test
    fun `uses the injected clock when the server sends no timestamp`() {
        val out = map(listOf(SessionMessage(id = 1, role = "user", content = "a")), now = 42L)
        assertEquals(42L, out.single().timestamp)
    }

    @Test
    fun `reads a server timestamp as seconds`() {
        val out =
            map(
                listOf(
                    SessionMessage(
                        id = 1,
                        role = "user",
                        content = "a",
                        timestamp = JsonPrimitive("1700000000"),
                    ),
                ),
            )
        assertEquals(1_700_000_000_000L, out.single().timestamp)
    }
}
