package com.m57.hermescontrol.ui.chat.components

import com.m57.hermescontrol.ui.chat.TodoItem

const val MAX_TODO_DEPTH = 6

data class TodoHierarchyRow(
    val todo: TodoItem,
    val sourceIndex: Int,
    val depth: Int,
) {
    // Source positions, not untrusted IDs, uniquely identify occurrences within this snapshot.
    val key: String get() = "todo-row-$sourceIndex"
}

/**
 * Flatten a plan without losing malformed relationships. Ambiguous duplicate parent IDs,
 * missing parents and self references remain roots. Rootless cycles are traversed after roots.
 * An iterative walk visits each occurrence once; visual depth is capped, not the number of rows.
 */
fun buildTodoHierarchy(todos: List<TodoItem>): List<TodoHierarchyRow> {
    val indicesById = todos.indices.groupBy { todos[it].id }
    val children = Array(todos.size) { mutableListOf<Int>() }
    val roots = mutableListOf<Int>()
    todos.forEachIndexed { index, todo ->
        val parentIndex =
            todo.parent
                ?.takeIf { it.isNotBlank() }
                ?.let { indicesById[it]?.singleOrNull() }
                ?.takeIf { it != index && todo.parent != todo.id }
        if (parentIndex == null) roots.add(index) else children[parentIndex].add(index)
    }

    val visited = BooleanArray(todos.size)
    val rows = ArrayList<TodoHierarchyRow>(todos.size)
    val stack = ArrayDeque<Pair<Int, Int>>()

    fun visit(start: Int) {
        stack.addLast(start to 0)
        while (stack.isNotEmpty()) {
            val (index, depth) = stack.removeLast()
            if (visited[index]) continue
            visited[index] = true
            rows.add(TodoHierarchyRow(todos[index], index, depth))
            for (child in children[index].asReversed()) {
                stack.addLast(child to (depth + 1).coerceAtMost(MAX_TODO_DEPTH))
            }
        }
    }
    for (root in roots) visit(root)
    for (index in todos.indices) {
        if (!visited[index]) visit(index)
    }
    return rows
}
