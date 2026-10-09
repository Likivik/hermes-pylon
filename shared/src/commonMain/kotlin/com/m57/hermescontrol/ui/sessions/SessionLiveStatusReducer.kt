package com.m57.hermescontrol.ui.sessions

import com.m57.hermescontrol.data.model.LiveSessionSnapshot
import com.m57.hermescontrol.data.model.SessionLiveStatus
import com.m57.hermescontrol.data.ws.WsEvent

/**
 * Transient state for tracking live active sessions in History.
 */
data class SessionLiveTrackingState(
    val liveStatuses: Map<String, SessionLiveStatus> = emptyMap(),
    val storedIdByRuntimeId: Map<String, String> = emptyMap(),
)

/**
 * Pure reducer managing live active session transitions.
 */
object SessionLiveStatusReducer {
    fun isLiveStatusEvent(event: WsEvent): Boolean =
        event is WsEvent.SessionInfo ||
            event is WsEvent.MessageStart ||
            event is WsEvent.MessageComplete ||
            event is WsEvent.MessageDone ||
            event is WsEvent.ApprovalRequest ||
            event is WsEvent.ClarifyRequest ||
            event is WsEvent.ClarifyExpire

    fun applySnapshot(
        state: SessionLiveTrackingState,
        snapshot: LiveSessionSnapshot,
    ): SessionLiveTrackingState =
        SessionLiveTrackingState(
            liveStatuses = snapshot.statusByStoredId,
            storedIdByRuntimeId = snapshot.storedIdByRuntimeId,
        )

    fun applyWsEvent(
        state: SessionLiveTrackingState,
        event: WsEvent,
    ): SessionLiveTrackingState =
        when (event) {
            is WsEvent.SessionInfo -> reduceSessionInfo(state, event)
            is WsEvent.MessageStart -> reduceMessageStart(state, event)
            is WsEvent.MessageComplete -> reduceMessageComplete(state, event)
            is WsEvent.MessageDone -> reduceMessageDone(state, event)
            is WsEvent.ApprovalRequest -> reduceApprovalRequest(state, event)
            is WsEvent.ClarifyRequest -> reduceClarifyRequest(state, event)
            is WsEvent.ClarifyExpire -> reduceResumedOutput(state, event.sessionId)
            is WsEvent.MessageToken -> reduceResumedOutput(state, event.sessionId)
            is WsEvent.ThinkingDelta -> reduceResumedOutput(state, event.sessionId)
            is WsEvent.ReasoningDelta -> reduceResumedOutput(state, event.sessionId)
            is WsEvent.ToolStart -> reduceResumedOutput(state, event.sessionId)
            is WsEvent.ToolProgress -> reduceResumedOutput(state, event.sessionId)
            else -> state
        }

    fun clear(): SessionLiveTrackingState = SessionLiveTrackingState()

    private fun reduceSessionInfo(
        state: SessionLiveTrackingState,
        event: WsEvent.SessionInfo,
    ): SessionLiveTrackingState {
        val data = event.data ?: return state
        val storedId = (data["stored_session_id"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
        val runtimeId =
            event.sessionId?.trim()?.takeIf { it.isNotEmpty() }
                ?: (data["session_id"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
        val running = data["running"] as? Boolean

        val targetStoredId = storedId ?: runtimeId?.let { state.storedIdByRuntimeId[it] }
        if (targetStoredId == null && runtimeId == null) {
            return state
        }

        val oldStoredId = runtimeId?.let { state.storedIdByRuntimeId[it] }
        val nextMapping =
            if (runtimeId != null && targetStoredId != null) {
                state.storedIdByRuntimeId + (runtimeId to targetStoredId)
            } else {
                state.storedIdByRuntimeId
            }

        if (targetStoredId == null) {
            return state.copy(storedIdByRuntimeId = nextMapping)
        }

        val nextStatuses = state.liveStatuses.toMutableMap()
        if (oldStoredId != null && oldStoredId != targetStoredId) {
            nextStatuses.remove(oldStoredId)
        }

        when (running) {
            true -> {
                nextStatuses[targetStoredId] = SessionLiveStatus.WORKING
            }

            false -> {
                nextStatuses.remove(targetStoredId)
            }

            null -> {
                // If running is not specified, leave status untouched
            }
        }

        return state.copy(
            liveStatuses = nextStatuses,
            storedIdByRuntimeId = nextMapping,
        )
    }

    private fun reduceMessageStart(
        state: SessionLiveTrackingState,
        event: WsEvent.MessageStart,
    ): SessionLiveTrackingState {
        val runtimeId = event.sessionId?.trim()?.takeIf { it.isNotEmpty() } ?: return state
        val storedId = state.storedIdByRuntimeId[runtimeId] ?: return state
        if (state.liveStatuses[storedId] == SessionLiveStatus.WORKING) return state
        return state.copy(
            liveStatuses = state.liveStatuses + (storedId to SessionLiveStatus.WORKING),
        )
    }

    private fun reduceMessageComplete(
        state: SessionLiveTrackingState,
        event: WsEvent.MessageComplete,
    ): SessionLiveTrackingState {
        val runtimeId = event.sessionId?.trim()?.takeIf { it.isNotEmpty() }
        val storedId = runtimeId?.let { state.storedIdByRuntimeId[it] } ?: return state

        if (!state.liveStatuses.containsKey(storedId)) return state
        return state.copy(
            liveStatuses = state.liveStatuses - storedId,
        )
    }

    private fun reduceMessageDone(
        state: SessionLiveTrackingState,
        event: WsEvent.MessageDone,
    ): SessionLiveTrackingState {
        val runtimeId = event.sessionId?.trim()?.takeIf { it.isNotEmpty() } ?: return state
        val storedId = state.storedIdByRuntimeId[runtimeId] ?: return state
        if (!state.liveStatuses.containsKey(storedId)) return state
        return state.copy(
            liveStatuses = state.liveStatuses - storedId,
        )
    }

    private fun reduceApprovalRequest(
        state: SessionLiveTrackingState,
        event: WsEvent.ApprovalRequest,
    ): SessionLiveTrackingState {
        val runtimeId = event.sessionId?.trim()?.takeIf { it.isNotEmpty() } ?: return state
        val storedId = state.storedIdByRuntimeId[runtimeId] ?: return state
        return state.copy(
            liveStatuses = state.liveStatuses + (storedId to SessionLiveStatus.WAITING),
        )
    }

    private fun reduceClarifyRequest(
        state: SessionLiveTrackingState,
        event: WsEvent.ClarifyRequest,
    ): SessionLiveTrackingState {
        val runtimeId = event.sessionId?.trim()?.takeIf { it.isNotEmpty() } ?: return state
        val storedId = state.storedIdByRuntimeId[runtimeId] ?: return state
        return state.copy(
            liveStatuses = state.liveStatuses + (storedId to SessionLiveStatus.WAITING),
        )
    }

    private fun reduceResumedOutput(
        state: SessionLiveTrackingState,
        runtimeSessionId: String?,
    ): SessionLiveTrackingState {
        val runtimeId = runtimeSessionId?.trim()?.takeIf { it.isNotEmpty() } ?: return state
        val storedId = state.storedIdByRuntimeId[runtimeId] ?: return state
        if (state.liveStatuses[storedId] != SessionLiveStatus.WAITING) return state
        return state.copy(
            liveStatuses = state.liveStatuses + (storedId to SessionLiveStatus.WORKING),
        )
    }
}
