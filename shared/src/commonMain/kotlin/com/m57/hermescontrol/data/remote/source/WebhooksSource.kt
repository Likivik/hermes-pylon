package com.m57.hermescontrol.data.remote.source

import com.m57.hermescontrol.data.model.CreateWebhookRequest
import com.m57.hermescontrol.data.model.DeleteWebhookResponse
import com.m57.hermescontrol.data.model.WebhookSubscription
import com.m57.hermescontrol.data.model.WebhookToggleSubscriptionRequest
import com.m57.hermescontrol.data.model.WebhooksResponse
import com.m57.hermescontrol.data.model.WebhooksToggleRequest
import com.m57.hermescontrol.data.remote.GatewayResponse

/**
 * What Webhooks needs from the gateway: 5 calls of the 150 it offers.
 *
 * Declared by the consumer, not by the gateway, so a fake for this screen implements 5 methods instead of
 * 150. `WebhooksSourceOf` is the production adapter; tests pass their own implementation.
 */
interface WebhooksSource {
    suspend fun createWebhook(body: CreateWebhookRequest): GatewayResponse<WebhookSubscription>

    suspend fun deleteWebhook(name: String): GatewayResponse<DeleteWebhookResponse>

    suspend fun getWebhooks(): GatewayResponse<WebhooksResponse>

    suspend fun setWebhookEnabled(
        name: String,
        body: WebhookToggleSubscriptionRequest,
    ): GatewayResponse<Unit>

    suspend fun toggleWebhooks(body: WebhooksToggleRequest): GatewayResponse<Unit>
}
