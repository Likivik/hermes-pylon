package com.m57.hermescontrol.data.remote.source

import com.m57.hermescontrol.data.model.MessagingPlatformResponse
import com.m57.hermescontrol.data.model.MessagingPlatformTestResult
import com.m57.hermescontrol.data.model.MessagingPlatformUpdate
import com.m57.hermescontrol.data.model.TelegramOnboardingApplyRequest
import com.m57.hermescontrol.data.model.TelegramOnboardingApplyResponse
import com.m57.hermescontrol.data.model.TelegramOnboardingStartRequest
import com.m57.hermescontrol.data.model.TelegramOnboardingStartResponse
import com.m57.hermescontrol.data.model.TelegramOnboardingStatusResponse
import com.m57.hermescontrol.data.remote.GatewayResponse

/**
 * What Channels needs from the gateway: 9 calls of the 150 it offers.
 *
 * Declared by the consumer, not by the gateway, so a fake for this screen implements 9 methods instead of
 * 150. `ChannelsSourceOf` is the production adapter; tests pass their own implementation.
 */
interface ChannelsSource {
    suspend fun applyTelegramOnboarding(
        pairingId: String,
        body: TelegramOnboardingApplyRequest,
    ): GatewayResponse<TelegramOnboardingApplyResponse>

    suspend fun cancelTelegramOnboarding(pairingId: String): GatewayResponse<Unit>

    suspend fun configurePlatform(
        platformId: String,
        config: MessagingPlatformUpdate,
    ): GatewayResponse<Unit>

    suspend fun getMessagingPlatforms(profile: String? = null): GatewayResponse<MessagingPlatformResponse>

    suspend fun getTelegramOnboardingStatus(pairingId: String): GatewayResponse<TelegramOnboardingStatusResponse>

    suspend fun removeMessagingPlatform(platformId: String): GatewayResponse<Unit>

    suspend fun restartGateway(): GatewayResponse<Unit>

    suspend fun startTelegramOnboarding(
        body: TelegramOnboardingStartRequest,
    ): GatewayResponse<TelegramOnboardingStartResponse>

    suspend fun testMessagingPlatform(platformId: String): GatewayResponse<MessagingPlatformTestResult>
}
