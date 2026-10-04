package com.m57.hermescontrol.ui.chat.components

import com.m57.hermescontrol.ui.chat.TodoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TodoHierarchyTest {
    private fun todo(
        id: String,
        parent: String? = null,
    ) = TodoItem(id, id, parent = parent)

    @Test
    fun `children follow their parent in source sibling order`() {
        val rows = buildTodoHierarchy(listOf(todo("child", "root"), todo("root"), todo("sibling", "root")))
        assertEquals(listOf("root", "child", "sibling"), rows.map { it.todo.id })
        assertEquals(listOf(0, 1, 1), rows.map { it.depth })
        assertEquals(listOf(1, 0, 2), rows.map { it.sourceIndex })
    }

    @Test
    fun `orphans self references and rootless cycles retain every occurrence`() {
        val todos =
            listOf(
                todo("a", "b"),
                todo("b", "a"),
                todo("orphan", "missing"),
                todo("self", "self"),
                todo("blank", " "),
                todo("cycle child", "b"),
            )
        val rows = buildTodoHierarchy(todos)
        assertEquals(todos.indices.toSet(), rows.map { it.sourceIndex }.toSet())
        assertEquals(todos.size, rows.size)
        assertEquals(listOf("orphan", "self", "blank", "a", "b", "cycle child"), rows.map { it.todo.id })
        assertEquals(listOf(0, 0, 0, 0, 1, 2), rows.map { it.depth })
    }

    @Test
    fun `duplicate IDs cannot alias keys or choose an ambiguous parent`() {
        val todos = listOf(todo("dup"), todo("dup"), todo("child", "dup"), todo("todo-row-0"))
        val rows = buildTodoHierarchy(todos)
        assertEquals(todos, rows.map { it.todo })
        assertEquals(todos.size, rows.map { it.key }.toSet().size)
        assertTrue(rows.all { it.depth == 0 })
    }

    @Test
    fun `deep chains cap indentation without truncating or recursive overflow`() {
        val todos = (0 until 20_000).map { todo("$it", if (it == 0) null else "${it - 1}") }
        val rows = buildTodoHierarchy(todos)
        assertEquals(todos, rows.map { it.todo })
        assertEquals(todos.size, rows.map { it.key }.toSet().size)
        assertEquals(MAX_TODO_DEPTH, rows.last().depth)
        assertTrue(rows.all { it.depth in 0..MAX_TODO_DEPTH })
    }

    @Test
    fun `flat and empty plans retain existing order`() {
        assertTrue(buildTodoHierarchy(emptyList()).isEmpty())
        val todos = listOf(todo("z"), todo("a"), todo("b"))
        assertEquals(todos, buildTodoHierarchy(todos).map { it.todo })
    }
}
