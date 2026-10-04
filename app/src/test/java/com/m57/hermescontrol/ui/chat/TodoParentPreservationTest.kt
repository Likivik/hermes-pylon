package com.m57.hermescontrol.ui.chat

import com.m57.hermescontrol.data.ws.WsEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class TodoParentPreservationTest {
    @Test
    fun `map envelopes preserve only nonblank string parents`() {
        val items =
            listOf(
                mapOf("id" to "child", "parent" to "root"),
                mapOf("id" to "missing"),
                mapOf("id" to "blank", "parent" to " "),
                mapOf("id" to "null", "parent" to null),
                mapOf("id" to "number", "parent" to 42),
                mapOf("id" to "boolean", "parent" to true),
            )
        for (envelope in listOf(null, "args", "parameters", "result")) {
            val data = if (envelope == null) mapOf("todos" to items) else mapOf(envelope to mapOf("todos" to items))
            assertEquals(listOf("root", null, null, null, null, null), extractTodosFromMap(data)?.map { it.parent })
        }
    }

    @Test
    fun `JSON envelopes reject null numeric boolean and structured parents`() {
        val payload =
            """
            {"todos":[
              {"id":"child","parent":"root"},
              {"id":"missing"},
              {"id":"blank","parent":" "},
              {"id":"null","parent":null},
              {"id":"number","parent":42},
              {"id":"boolean","parent":true},
              {"id":"object","parent":{}},
              {"id":"array","parent":[]}
            ]}
            """.trimIndent()
        for (envelope in listOf(null, "args", "parameters", "result")) {
            val json = if (envelope == null) payload else "{\"$envelope\":$payload}"
            assertEquals(
                listOf("root", null, null, null, null, null, null, null),
                extractTodosFromJson(json)?.map { it.parent },
            )
        }
    }

    @Test
    fun `tool completion preserves hierarchy through persisted message history`() {
        val state = ChatUiState(currentSessionId = "session")
        val started =
            ChatWsEventReducer.reduce(
                state,
                StreamingState(),
                WsEvent.ToolStart("todo", mapOf("tool_call_id" to "call"), "session"),
                "session",
            )
        val result =
            ChatWsEventReducer.reduce(
                started.state,
                started.streamingState,
                WsEvent.ToolComplete(
                    "todo",
                    mapOf(
                        "tool_call_id" to "call",
                        "result" to mapOf("todos" to listOf(mapOf("id" to "child", "parent" to "root"))),
                    ),
                    "session",
                ),
                "session",
            )
        assertEquals("root", result.state.todos.single().parent)
        val persisted = result.effects.filterIsInstance<ReducerEffect.PersistMessage>().single().message
        assertEquals(result.state.todos, hydrateTodosFromMessages(listOf(persisted)))
        assertEquals(result.state.todos, hydrateTodosFromMessages(result.state.messages))
    }

    @Test
    fun `tool start args retain parents and hydrate without live todo events`() {
        val result =
            ChatWsEventReducer.reduce(
                ChatUiState(currentSessionId = "session"),
                StreamingState(),
                WsEvent.ToolStart(
                    "todo",
                    mapOf("args" to mapOf("todos" to listOf(mapOf("id" to "child", "parent" to "root")))),
                    "session",
                ),
                "session",
            )
        assertEquals("root", result.state.todos.single().parent)
        assertEquals(result.state.todos, hydrateTodosFromMessages(result.state.messages))
    }
}
