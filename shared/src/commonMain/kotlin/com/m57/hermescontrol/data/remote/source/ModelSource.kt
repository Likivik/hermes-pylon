package com.m57.hermescontrol.data.remote.source

import com.m57.hermescontrol.data.model.ActiveProfileResponse
import com.m57.hermescontrol.data.model.AuxiliaryModelsResponse
import com.m57.hermescontrol.data.model.MoaConfigResponse
import com.m57.hermescontrol.data.model.ModelAssignmentRequest
import com.m57.hermescontrol.data.model.ModelAssignmentResponse
import com.m57.hermescontrol.data.model.ModelOptionsResponse
import com.m57.hermescontrol.data.model.ProfilesResponse
import com.m57.hermescontrol.data.model.UpdateProfileModelRequest
import com.m57.hermescontrol.data.remote.GatewayResponse

/**
 * What Model needs from the gateway: 8 calls of the 150 it offers.
 *
 * Declared by the consumer, not by the gateway, so a fake for this screen implements 8 methods instead of
 * 150. `ModelSourceOf` is the production adapter; tests pass their own implementation.
 */
interface ModelSource {
    suspend fun getActiveProfile(): GatewayResponse<ActiveProfileResponse>

    suspend fun getAuxiliaryModels(): GatewayResponse<AuxiliaryModelsResponse>

    suspend fun getMoaModels(): GatewayResponse<MoaConfigResponse>

    suspend fun getModelOptions(
        refresh: Boolean = false,
        includeUnconfigured: Boolean = true,
    ): GatewayResponse<ModelOptionsResponse>

    suspend fun getProfiles(): GatewayResponse<ProfilesResponse>

    suspend fun saveMoaModels(body: MoaConfigResponse): GatewayResponse<MoaConfigResponse>

    suspend fun setModelAssignment(body: ModelAssignmentRequest): GatewayResponse<ModelAssignmentResponse>

    suspend fun updateProfileModel(
        name: String,
        body: UpdateProfileModelRequest,
    ): GatewayResponse<Unit>
}
