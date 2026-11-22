package com.m57.hermescontrol.data.remote.source

import com.m57.hermescontrol.data.model.CreateTaskBody
import com.m57.hermescontrol.data.model.KanbanBoardResponse
import com.m57.hermescontrol.data.model.KanbanBoardsResponse
import com.m57.hermescontrol.data.model.KanbanTask
import com.m57.hermescontrol.data.remote.GatewayResponse

/**
 * What Kanban needs from the gateway: 5 calls of the 150 it offers.
 *
 * Declared by the consumer, not by the gateway, so a fake for this screen implements 5 methods instead of
 * 150. `KanbanSourceOf` is the production adapter; tests pass their own implementation.
 */
interface KanbanSource {
    suspend fun createKanbanTask(
        board: String?,
        task: CreateTaskBody,
    ): GatewayResponse<KanbanTask>

    suspend fun getKanbanBoard(): GatewayResponse<KanbanBoardResponse>

    suspend fun getKanbanBoards(): GatewayResponse<KanbanBoardsResponse>

    suspend fun switchKanbanBoard(slug: String): GatewayResponse<Unit>

    suspend fun updateKanbanTask(
        taskId: String,
        body: Map<String, String?>,
    ): GatewayResponse<Unit>
}
