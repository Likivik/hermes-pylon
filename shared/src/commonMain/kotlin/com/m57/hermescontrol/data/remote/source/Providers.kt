package com.m57.hermescontrol.data.remote.source

import com.m57.hermescontrol.data.model.OAuthPollResponse
import com.m57.hermescontrol.data.model.OAuthProvidersResponse
import com.m57.hermescontrol.data.model.OAuthStartResponse
import com.m57.hermescontrol.data.model.OAuthSubmitRequest
import com.m57.hermescontrol.data.model.OAuthSubmitResponse
import com.m57.hermescontrol.data.remote.GatewayResponse

/**
 * What Providers needs from the gateway: 6 calls of the 150 it offers.
 *
 * Declared by the consumer, not by the gateway, so a fake for this screen implements 6 methods instead of
 * 150. `DefaultProviders` is the production adapter; tests pass their own implementation.
 */
interface Providers {
    suspend fun cancelOAuthSession(sessionId: String): GatewayResponse<Unit>

    suspend fun disconnectOAuthProvider(providerId: String): GatewayResponse<Unit>

    suspend fun getOAuthProviders(): GatewayResponse<OAuthProvidersResponse>

    suspend fun pollOAuthSession(
        providerId: String,
        sessionId: String,
    ): GatewayResponse<OAuthPollResponse>

    suspend fun startOAuthLogin(providerId: String): GatewayResponse<OAuthStartResponse>

    suspend fun submitOAuthCode(
        providerId: String,
        body: OAuthSubmitRequest,
    ): GatewayResponse<OAuthSubmitResponse>
}
