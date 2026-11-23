package com.m57.hermescontrol.data.remote.source

import com.m57.hermescontrol.data.model.CreateCronJobRequest
import com.m57.hermescontrol.data.model.CronJob
import com.m57.hermescontrol.data.model.UpdateCronJobRequest
import com.m57.hermescontrol.data.remote.GatewayResponse

/**
 * What CronJobs needs from the gateway: 8 calls of the 150 it offers.
 *
 * Declared by the consumer, not by the gateway, so a fake for this screen implements 8 methods instead of
 * 150. `DefaultCronJobs` is the production adapter; tests pass their own implementation.
 */
interface CronJobs {
    suspend fun createCronJob(body: CreateCronJobRequest): GatewayResponse<CronJob>

    suspend fun deleteCronJob(id: String): GatewayResponse<Unit>

    suspend fun getCronJob(id: String): GatewayResponse<CronJob>

    suspend fun getCronJobs(): GatewayResponse<List<CronJob>>

    suspend fun pauseCronJob(id: String): GatewayResponse<Unit>

    suspend fun resumeCronJob(id: String): GatewayResponse<Unit>

    suspend fun triggerCronJob(id: String): GatewayResponse<Unit>

    suspend fun updateCronJob(
        id: String,
        body: UpdateCronJobRequest,
    ): GatewayResponse<CronJob>
}
