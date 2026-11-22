package com.m57.hermescontrol.ui.chat

import com.m57.hermescontrol.data.remote.ApiClient
import com.m57.hermescontrol.data.remote.HermesGatewayApi

/**
 * The chat screen's view of the HTTP client, as a seam instead of a global.
 *
 * Same reason as [ChatTransport]: `ApiClient` is an `object`, so every test that touches it mocks a
 * static. This names the five members the chat screen uses.
 */
interface ChatApi {
    val hermesApi: HermesGatewayApi

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
    override val hermesApi: HermesGatewayApi get() = ApiClient.hermesApi

    override val selectedProfileId: String? get() = ApiClient.selectedProfileId

    override suspend fun currentProfileName(): String? = ApiClient.currentProfileName()

    override suspend fun loginWithPassword(
        jsonBody: String,
        timeoutMillis: Long,
    ): Int = ApiClient.hermesApi.loginWithPassword(jsonBody, timeoutMillis).code

    override fun setSelectedProfile(profile: String?) = ApiClient.setSelectedProfile(profile)

    override fun clearSelectedProfile() = ApiClient.clearSelectedProfile()
}
