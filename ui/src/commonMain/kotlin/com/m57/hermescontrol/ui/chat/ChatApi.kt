package com.m57.hermescontrol.ui.chat

import com.m57.hermescontrol.data.remote.GatewayConnection
import com.m57.hermescontrol.data.remote.HermesApi

/**
 * The chat screen's view of the HTTP client, as a seam instead of a global.
 *
 * Same reason as [ChatTransport]: `GatewayConnection` is an `object`, so every test that touches it mocks a
 * static. This names the five members the chat screen uses.
 */
interface ChatApi {
    val hermesApi: HermesApi

    val selectedProfileId: String?

    suspend fun currentProfileName(): String?

    suspend fun loginWithPassword(
        jsonBody: String,
        timeoutMillis: Long = 10_000,
    ): Int

    fun setSelectedProfile(profile: String?)

    fun clearSelectedProfile()
}

/** Production adapter: the singleton, unchanged. */
object DefaultChatApi : ChatApi {
    override val hermesApi: HermesApi get() = GatewayConnection.hermesApi

    override val selectedProfileId: String? get() = GatewayConnection.selectedProfileId

    override suspend fun currentProfileName(): String? = GatewayConnection.currentProfileName()

    override suspend fun loginWithPassword(
        jsonBody: String,
        timeoutMillis: Long,
    ): Int = GatewayConnection.hermesApi.loginWithPassword(jsonBody, timeoutMillis).code

    override fun setSelectedProfile(profile: String?) = GatewayConnection.setSelectedProfile(profile)

    override fun clearSelectedProfile() = GatewayConnection.clearSelectedProfile()
}
