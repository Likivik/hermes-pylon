package com.m57.hermescontrol.data.remote.source

import com.m57.hermescontrol.data.model.ActiveProfileResponse
import com.m57.hermescontrol.data.model.CreateProfileRequest
import com.m57.hermescontrol.data.model.ModelOptionsResponse
import com.m57.hermescontrol.data.model.ProfileSoulResponse
import com.m57.hermescontrol.data.model.ProfilesResponse
import com.m57.hermescontrol.data.model.SetActiveProfileRequest
import com.m57.hermescontrol.data.model.Skill
import com.m57.hermescontrol.data.model.SkillHubSearchResponse
import com.m57.hermescontrol.data.model.UpdateProfileDescriptionRequest
import com.m57.hermescontrol.data.model.UpdateProfileModelRequest
import com.m57.hermescontrol.data.model.UpdateProfileSoulRequest
import com.m57.hermescontrol.data.remote.GatewayResponse

/**
 * What Profiles needs from the gateway: 11 calls of the 150 it offers.
 *
 * Declared by the consumer, not by the gateway, so a fake for this screen implements 11 methods instead of
 * 150. `DefaultProfiles` is the production adapter; tests pass their own implementation.
 */
interface Profiles {
    suspend fun createProfile(body: CreateProfileRequest): GatewayResponse<Unit>

    suspend fun getActiveProfile(): GatewayResponse<ActiveProfileResponse>

    suspend fun getModelOptions(
        refresh: Boolean = false,
        includeUnconfigured: Boolean = true,
    ): GatewayResponse<ModelOptionsResponse>

    suspend fun getProfileSoul(name: String): GatewayResponse<ProfileSoulResponse>

    suspend fun getProfiles(): GatewayResponse<ProfilesResponse>

    suspend fun getSkills(): GatewayResponse<List<Skill>>

    suspend fun searchSkillsHub(
        q: String,
        source: String? = null,
        limit: Int? = null,
    ): GatewayResponse<SkillHubSearchResponse>

    suspend fun setActiveProfile(body: SetActiveProfileRequest): GatewayResponse<Unit>

    suspend fun updateProfileDescription(
        name: String,
        body: UpdateProfileDescriptionRequest,
    ): GatewayResponse<Unit>

    suspend fun updateProfileModel(
        name: String,
        body: UpdateProfileModelRequest,
    ): GatewayResponse<Unit>

    suspend fun updateProfileSoul(
        name: String,
        body: UpdateProfileSoulRequest,
    ): GatewayResponse<Unit>
}
