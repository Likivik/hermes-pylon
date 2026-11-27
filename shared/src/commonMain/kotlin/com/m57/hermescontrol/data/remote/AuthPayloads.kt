package com.m57.hermescontrol.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import com.m57.hermescontrol.util.HermesLog

private const val TAG = "AuthPayloads"

@Serializable
private data class PasswordLoginPayload(
    val provider: String,
    val username: String,
    val password: String,
    val next: String,
)

@Serializable
private data class WebSocketTicketPayload(
    val ticket: String,
)

/** JSON serialization for authentication requests and responses. */
object AuthPayloads {
    fun passwordLogin(
        username: String,
        password: String,
    ): String =
        HermesJson.instance.encodeToString(
            PasswordLoginPayload(
                provider = "basic",
                username = username,
                password = password,
                next = "",
            ),
        )

    fun webSocketTicket(body: String): String? =
        runCatching {
            HermesJson.instance.decodeFromString<WebSocketTicketPayload>(body)
                .ticket
                .takeIf { it.isNotBlank() }
        }.onFailure { HermesLog.w(TAG, "the websocket ticket did not parse (${it::class.simpleName})") }
            .getOrNull()
}
