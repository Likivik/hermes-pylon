package com.m57.hermescontrol.data.ws

import com.m57.hermescontrol.data.local.AuthManager
import com.m57.hermescontrol.data.model.ActiveSessionsResponse
import com.m57.hermescontrol.data.model.LiveSessionSnapshot
import com.m57.hermescontrol.data.model.SessionLiveStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.serializer

/**
 * Interface for polling and observing live session statuses.
 */
interface SessionLiveStatusSource {
    suspend fun fetchActiveSessionsSnapshot(connection: SessionLiveConnection): LiveSessionSnapshot?

    val sourcedEvents: Flow<SourcedSessionLiveEvent>
    val connectionStatus: StateFlow<ConnectionStatus>
    val selectedProfileIds: Flow<String>

    fun currentConnection(profileId: String): SessionLiveConnection?
}

data class SessionLiveConnection(
    val profileId: String,
    val generation: Int,
)

data class SourcedSessionLiveEvent(
    val event: WsEvent,
    val profileId: String?,
    val connectionGeneration: Int,
)

/**
 * Decodes untyped RPC responses from `session.active_list` into a [LiveSessionSnapshot].
 */
object SessionLiveStatusDecoder {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

    fun decodeSnapshot(rawResult: Any?): LiveSessionSnapshot? {
        if (rawResult == null) return null
        val response =
            try {
                decodeResponse(rawResult)
            } catch (_: Exception) {
                return null
            } ?: return null

        val statusByStoredId = mutableMapOf<String, SessionLiveStatus>()
        val storedIdByRuntimeId = mutableMapOf<String, String>()

        for (item in response.sessions) {
            val runtimeId = item.id?.trim()
            val storedId = item.sessionKey?.trim()
            if (runtimeId.isNullOrEmpty() || storedId.isNullOrEmpty()) {
                continue
            }
            storedIdByRuntimeId[runtimeId] = storedId
            when (item.status?.trim()?.lowercase()) {
                "working" -> {
                    statusByStoredId[storedId] = SessionLiveStatus.WORKING
                }

                "waiting" -> {
                    statusByStoredId[storedId] = SessionLiveStatus.WAITING
                }

                else -> {
                    // idle, starting, unknown -> no live indicator
                }
            }
        }

        return LiveSessionSnapshot(
            statusByStoredId = statusByStoredId,
            storedIdByRuntimeId = storedIdByRuntimeId,
        )
    }

    private fun decodeResponse(result: Any): ActiveSessionsResponse? {
        val element: JsonElement =
            when (result) {
                is JsonElement -> {
                    result
                }

                is Map<*, *> -> {
                    anyToJsonElement(result)
                }

                is List<*> -> {
                    JsonArray(result.map { anyToJsonElement(it) })
                }

                else -> {
                    val str = result.toString()
                    try {
                        json.parseToJsonElement(str)
                    } catch (_: Exception) {
                        return null
                    }
                }
            }
        if (element !is JsonObject || !element.containsKey("sessions")) {
            return null
        }
        return json.decodeFromJsonElement(serializer<ActiveSessionsResponse>(), element)
    }

    @Suppress("UNCHECKED_CAST")
    internal fun anyToJsonElement(value: Any?): JsonElement =
        when (value) {
            null -> {
                JsonNull
            }

            is JsonElement -> {
                value
            }

            is Map<*, *> -> {
                JsonObject(
                    (value as Map<String, Any?>).mapValues { (_, v) -> anyToJsonElement(v) },
                )
            }

            is List<*> -> {
                JsonArray(value.map { anyToJsonElement(it) })
            }

            is String -> {
                JsonPrimitive(value)
            }

            is Boolean -> {
                JsonPrimitive(value)
            }

            is Number -> {
                JsonPrimitive(value)
            }

            else -> {
                JsonPrimitive(value.toString())
            }
        }
}

/**
 * Production implementation of [SessionLiveStatusSource] backed by [HermesWsClient].
 */
class HermesSessionLiveStatusSource(
    private val boundRpcRequest:
        suspend (connection: SessionLiveConnection, method: String, params: Map<String, Any>) -> Any? =
        { connection, method, params ->
            val binding =
                HermesWsClient.connectionBinding(connection.profileId)
                    ?.takeIf { it.generation == connection.generation }
                    ?: throw HermesWsClient.HermesRpcException("WebSocket binding changed — request cancelled")
            HermesWsClient.requestForProfileConnectionAwaited(binding, method, params)
        },
    sourcedEventsProvider: () -> Flow<SourcedSessionLiveEvent> = {
        HermesWsClient.sourcedEvents.map { sourced ->
            SourcedSessionLiveEvent(sourced.event, sourced.profileId, sourced.connectionGeneration)
        }
    },
    connectionStatusProvider: () -> StateFlow<ConnectionStatus> = { HermesWsClient.connectionStatus },
    selectedProfileIdsProvider: () -> Flow<String> = { AuthManager.selectedProfileIdFlow },
    private val currentConnectionProvider: (String) -> SessionLiveConnection? = { profileId ->
        HermesWsClient.connectionBinding(profileId)?.let { SessionLiveConnection(it.profileId, it.generation) }
    },
    private val isConnectionCurrent: (SessionLiveConnection) -> Boolean = { connection ->
        AuthManager.getSelectedProfileId() == connection.profileId &&
            HermesWsClient.connectionBinding(connection.profileId)?.generation == connection.generation
    },
) : SessionLiveStatusSource {
    override val sourcedEvents: Flow<SourcedSessionLiveEvent> by lazy { sourcedEventsProvider() }
    override val connectionStatus: StateFlow<ConnectionStatus> by lazy { connectionStatusProvider() }
    override val selectedProfileIds: Flow<String> by lazy { selectedProfileIdsProvider() }

    override fun currentConnection(profileId: String): SessionLiveConnection? = currentConnectionProvider(profileId)

    override suspend fun fetchActiveSessionsSnapshot(connection: SessionLiveConnection): LiveSessionSnapshot? =
        try {
            if (!isConnectionCurrent(connection)) return null
            val raw = boundRpcRequest(connection, WsMethods.SESSION_ACTIVE_LIST, emptyMap())
            if (!isConnectionCurrent(connection)) null else SessionLiveStatusDecoder.decodeSnapshot(raw)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
}
