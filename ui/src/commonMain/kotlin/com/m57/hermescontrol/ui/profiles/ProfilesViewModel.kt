package com.m57.hermescontrol.ui.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m57.hermescontrol.AppEnvironment
import com.m57.hermescontrol.data.model.CreateProfileRequest
import com.m57.hermescontrol.data.model.HubSkill
import com.m57.hermescontrol.data.model.ModelProvider
import com.m57.hermescontrol.data.model.ProfileInfo
import com.m57.hermescontrol.data.model.SetActiveProfileRequest
import com.m57.hermescontrol.data.model.Skill
import com.m57.hermescontrol.data.model.UpdateProfileDescriptionRequest
import com.m57.hermescontrol.data.model.UpdateProfileModelRequest
import com.m57.hermescontrol.data.model.UpdateProfileSoulRequest
import com.m57.hermescontrol.data.remote.GatewayConnection
import com.m57.hermescontrol.data.remote.NetworkResult
import com.m57.hermescontrol.data.remote.safeApiCall
import com.m57.hermescontrol.data.remote.source.DefaultProfiles
import com.m57.hermescontrol.data.remote.source.Profiles
import com.m57.hermescontrol.ui.common.ToastHost
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.m57.hermescontrol.util.ioDispatcher as ioDispatcher

data class ProfilesUiState(
    val isLoading: Boolean = false,
    val profiles: List<ProfileInfo> = emptyList(),
    val activeProfileName: String? = null,
    /** What the gateway process actually serves (`current`); may lag the selection. */
    val activatedProfileName: String? = null,
    /** Set while the selection has not been activated by the gateway. */
    val pendingProfileName: String? = null,
    val selectedSoulContent: String? = null,
    val isLoadingSoul: Boolean = false,
    val errorMessage: String? = null,
    val toastMessage: String? = null,
    // Profile Builder states
    val modelProviders: List<ModelProvider> = emptyList(),
    val isLoadingBuilderData: Boolean = false,
    val availableSkills: List<Skill> = emptyList(),
    val hubSearchResults: List<HubSkill> = emptyList(),
    val isSearchingHub: Boolean = false,
)

class ProfilesViewModel(
    private val ioDispatcher: CoroutineDispatcher = AppEnvironment.current.io,
    private val source: Profiles = DefaultProfiles(GatewayConnection.hermesApi),
) :
    ViewModel(),
        ToastHost {
    private val _uiState = MutableStateFlow(ProfilesUiState())
    val uiState: StateFlow<ProfilesUiState> = _uiState.asStateFlow()

    fun loadProfiles() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                coroutineScope {
                    val profilesDeferred = async(ioDispatcher) { safeApiCall { source.getProfiles() } }
                    val activeDeferred =
                        async(ioDispatcher) { safeApiCall { source.getActiveProfile() } }

                    val profilesResult = profilesDeferred.await()
                    val activeResult = activeDeferred.await()

                    if (profilesResult is NetworkResult.Success && activeResult is NetworkResult.Success) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                profiles = profilesResult.data.profiles.orEmpty(),
                                activeProfileName = activeResult.data.active,
                                activatedProfileName = activeResult.data.current,
                            )
                        }
                        // First sight of the gateway: adopt the serving profile as the
                        // selection, so requests are scoped before the user picks anything.
                        if (GatewayConnection.selectedProfileId == null) {
                            GatewayConnection.setSelectedProfile(
                                activeResult.data.current ?: activeResult.data.active,
                            )
                        }
                    } else {
                        val profilesError = (profilesResult as? NetworkResult.Failure)?.error?.message ?: "Success"
                        val activeError = (activeResult as? NetworkResult.Failure)?.error?.message ?: "Success"
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage =
                                    "Failed to load profiles/active: " +
                                        "Profiles: $profilesError, Active: $activeError",
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Failed to load profiles: ${e.message}")
                }
            }
        }
    }

    /**
     * Select a profile and scope the app to it.
     *
     * Success means the *app* now reads that profile's data — the client is rebuilt with
     * `profile=<name>`, so every request follows. It does **not** mean the gateway process
     * switched: `POST /api/profiles/active` only writes a preference, and `current` does not
     * follow it (measured; see #208). When the two differ the UI says so instead of claiming
     * a switch that did not happen.
     */
    fun selectActiveProfile(name: String) {
        val previous = _uiState.value.activeProfileName
        ProfileScope.repository.select(name)
        _uiState.update { it.copy(activeProfileName = name, pendingProfileName = name) }

        viewModelScope.launch {
            val result =
                withContext(ioDispatcher) {
                    safeApiCall { source.setActiveProfile(SetActiveProfileRequest(name)) }
                }
            when (result) {
                is NetworkResult.Success -> {
                    val pair =
                        withContext(ioDispatcher) {
                            safeApiCall { source.getActiveProfile() }
                        }
                    val activated = (pair as? NetworkResult.Success)?.data?.current
                    val outcome = profileSwitchOutcome(name, activated)
                    _uiState.update {
                        it.copy(
                            activatedProfileName = activated,
                            pendingProfileName = (outcome as? ProfileSwitch.Pending)?.selected,
                            toastMessage =
                                when (outcome) {
                                    is ProfileSwitch.Activated -> "Switched to profile $name"
                                    is ProfileSwitch.Pending ->
                                        "Browsing $name — the gateway still serves ${outcome.serving ?: "another profile"}"
                                },
                        )
                    }
                    loadProfiles()
                }

                is NetworkResult.Failure -> {
                    // The request never landed: put the app back where the user was.
                    GatewayConnection.setSelectedProfile(previous)
                    _uiState.update {
                        it.copy(
                            activeProfileName = previous,
                            pendingProfileName = null,
                            toastMessage = "Failed to switch profile: ${result.error.message}",
                        )
                    }
                }
            }
        }
    }

    fun loadSoul(profileName: String) {
        _uiState.update { it.copy(isLoadingSoul = true, selectedSoulContent = null) }
        viewModelScope.launch {
            val result =
                withContext(ioDispatcher) {
                    safeApiCall { source.getProfileSoul(profileName) }
                }
            when (result) {
                is NetworkResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoadingSoul = false,
                            selectedSoulContent = result.data.content,
                        )
                    }
                }

                is NetworkResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isLoadingSoul = false,
                            toastMessage = "Failed to load soul: ${result.error.message}",
                        )
                    }
                }
            }
        }
    }

    fun saveSoul(
        profileName: String,
        content: String,
    ) {
        viewModelScope.launch {
            val result =
                withContext(ioDispatcher) {
                    safeApiCall {
                        source.updateProfileSoul(
                            profileName,
                            UpdateProfileSoulRequest(content),
                        )
                    }
                }
            when (result) {
                is NetworkResult.Success -> {
                    _uiState.update {
                        it.copy(
                            selectedSoulContent = null,
                            toastMessage = "Soul updated successfully",
                        )
                    }
                    loadProfiles()
                }

                is NetworkResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            toastMessage = "Failed to save soul: ${result.error.message}",
                        )
                    }
                }
            }
        }
    }

    fun updateModel(
        profileName: String,
        provider: String,
        model: String,
    ) {
        viewModelScope.launch {
            val result =
                withContext(ioDispatcher) {
                    safeApiCall {
                        source.updateProfileModel(
                            profileName,
                            UpdateProfileModelRequest(provider, model),
                        )
                    }
                }
            when (result) {
                is NetworkResult.Success -> {
                    _uiState.update { it.copy(toastMessage = "Model settings updated") }
                    loadProfiles()
                }

                is NetworkResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            toastMessage = "Failed to update model settings: ${result.error.message}",
                        )
                    }
                }
            }
        }
    }

    override fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    fun closeSoulDialog() {
        _uiState.update { it.copy(selectedSoulContent = null) }
    }

    fun cloneProfile(
        sourceProfileName: String,
        newProfileName: String,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result =
                withContext(ioDispatcher) {
                    safeApiCall {
                        source.createProfile(
                            CreateProfileRequest(
                                name = newProfileName,
                                clone_from = sourceProfileName,
                            ),
                        )
                    }
                }
            when (result) {
                is NetworkResult.Success -> {
                    _uiState.update {
                        it.copy(
                            toastMessage = "Profile '$sourceProfileName' cloned successfully to '$newProfileName'",
                        )
                    }
                    loadProfiles()
                }

                is NetworkResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            toastMessage = "Failed to clone profile '$sourceProfileName': ${result.error.message}",
                        )
                    }
                }
            }
        }
    }

    fun updateProfileDescription(
        profileName: String,
        description: String,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result =
                withContext(ioDispatcher) {
                    safeApiCall {
                        source.updateProfileDescription(
                            profileName,
                            UpdateProfileDescriptionRequest(description),
                        )
                    }
                }
            when (result) {
                is NetworkResult.Success -> {
                    _uiState.update {
                        it.copy(
                            toastMessage = "Profile '$profileName' description updated",
                        )
                    }
                    loadProfiles()
                }

                is NetworkResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            toastMessage = "Failed to update description for '$profileName': ${result.error.message}",
                        )
                    }
                }
            }
        }
    }

    fun loadBuilderData() {
        _uiState.update { it.copy(isLoadingBuilderData = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                coroutineScope {
                    val modelsDeferred = async(ioDispatcher) { safeApiCall { source.getModelOptions() } }
                    val skillsDeferred = async(ioDispatcher) { safeApiCall { source.getSkills() } }

                    val modelsResult = modelsDeferred.await()
                    val skillsResult = skillsDeferred.await()

                    if (modelsResult is NetworkResult.Success && skillsResult is NetworkResult.Success) {
                        _uiState.update {
                            it.copy(
                                isLoadingBuilderData = false,
                                modelProviders = modelsResult.data.providers,
                                availableSkills = skillsResult.data,
                            )
                        }
                    } else {
                        val modelsError = (modelsResult as? NetworkResult.Failure)?.error?.message ?: "Success"
                        val skillsError = (skillsResult as? NetworkResult.Failure)?.error?.message ?: "Success"
                        _uiState.update {
                            it.copy(
                                isLoadingBuilderData = false,
                                errorMessage =
                                    "Failed to load builder data: " +
                                        "Models: $modelsError, Skills: $skillsError",
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingBuilderData = false,
                        errorMessage = "Failed to load builder data: ${e.message}",
                    )
                }
            }
        }
    }

    fun searchHub(query: String) {
        if (query.isBlank()) return
        _uiState.update { it.copy(isSearchingHub = true) }
        viewModelScope.launch {
            val result =
                withContext(ioDispatcher) {
                    safeApiCall { source.searchSkillsHub(query) }
                }
            when (result) {
                is NetworkResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isSearchingHub = false,
                            hubSearchResults = result.data.results.orEmpty(),
                        )
                    }
                }

                is NetworkResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isSearchingHub = false,
                            toastMessage = "Failed to search skills hub: ${result.error.message}",
                        )
                    }
                }
            }
        }
    }

    fun createProfile(
        request: CreateProfileRequest,
        onSuccess: () -> Unit,
    ) {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val result =
                withContext(ioDispatcher) {
                    safeApiCall { source.createProfile(request) }
                }
            when (result) {
                is NetworkResult.Success -> {
                    _uiState.update {
                        it.copy(
                            toastMessage = "Profile ${request.name} created successfully",
                        )
                    }
                    loadProfiles()
                    onSuccess()
                }

                is NetworkResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            toastMessage = "Failed to create profile: ${result.error.message}",
                        )
                    }
                }
            }
        }
    }
}
