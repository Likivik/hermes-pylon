package com.m57.hermescontrol.ui.gateway

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m57.hermescontrol.AppEnvironment
import com.m57.hermescontrol.data.model.StatusResponse
import com.m57.hermescontrol.data.remote.GatewayConnection
import com.m57.hermescontrol.data.remote.NetworkResult
import com.m57.hermescontrol.data.remote.safeApiCall
import com.m57.hermescontrol.data.remote.source.DefaultGateways
import com.m57.hermescontrol.data.remote.source.Gateways
import com.m57.hermescontrol.ui.common.ToastHost
import com.m57.hermescontrol.ui.common.reconcilePressureStatus
import com.m57.hermescontrol.ui.common.safeLaunchLoad
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.m57.hermescontrol.util.ioDispatcher as ioDispatcher

data class GatewayUiState(
    val isLoading: Boolean = false,
    val isActionRunning: Boolean = false,
    val status: StatusResponse? = null,
    val errorMessage: String? = null,
    val toastMessage: String? = null,
)

class GatewayViewModel(
    private val ioDispatcher: CoroutineDispatcher = AppEnvironment.current.io,
    private val source: Gateways = DefaultGateways(GatewayConnection.hermesApi),
) :
    ViewModel(),
        ToastHost {
    private val _uiState = MutableStateFlow(GatewayUiState())
    val uiState: StateFlow<GatewayUiState> = _uiState.asStateFlow()

    private var profileId: String? = null
    private var statusGeneration = 0L
    private var actionGeneration = 0L

    fun onProfileChanged(newProfileId: String) {
        if (profileId == newProfileId) return
        statusGeneration += 1
        actionGeneration += 1
        profileId = newProfileId
        _uiState.value = GatewayUiState()
        loadStatus()
    }

    fun loadStatus() {
        val generation = ++statusGeneration
        val requestedProfileId = profileId
        safeLaunchLoad(
            ioDispatcher = ioDispatcher,
            apiCall = { safeApiCall { source.getStatus() } },
            onStart = { _uiState.update { it.copy(isLoading = true, errorMessage = null) } },
            onSuccess = { data ->
                if (profileId == requestedProfileId && generation == statusGeneration) {
                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            status =
                                if (data.memory == null && data.disk == null) {
                                    data
                                } else {
                                    reconcilePressureStatus(current.status, data)
                                },
                        )
                    }
                }
            },
            onError = { errorMsg ->
                if (profileId == requestedProfileId && generation == statusGeneration) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Failed to load status: $errorMsg",
                            status = null,
                        )
                    }
                }
            },
        )
    }

    fun startGateway() {
        runGatewayAction("start") { gateway -> safeApiCall { gateway.startGateway() } }
    }

    fun stopGateway() {
        runGatewayAction("stop") { gateway -> safeApiCall { gateway.stopGateway() } }
    }

    fun restartGateway() {
        runGatewayAction("restart") { gateway -> safeApiCall { gateway.restartGateway() } }
    }

    private fun runGatewayAction(
        actionName: String,
        apiCall: suspend (Gateways) -> NetworkResult<Unit>,
    ) {
        if (_uiState.value.isActionRunning) return
        val requestedProfileId = profileId
        // Re-read per action: a profile or endpoint change rebuilds the client, and this action must travel the
        // current one. The generation number is the fence — the object itself is not compared.
        val startedAt = GatewayConnection.generation
        val gateway = DefaultGateways(GatewayConnection.hermesApi)
        val generation = ++actionGeneration
        _uiState.update { it.copy(isActionRunning = true) }
        viewModelScope.launch {
            val result = withContext(ioDispatcher) { apiCall(gateway) }
            val stale =
                profileId != requestedProfileId ||
                    generation != actionGeneration ||
                    startedAt != GatewayConnection.generation
            if (stale) return@launch
            when (result) {
                is NetworkResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isActionRunning = false,
                            toastMessage = "Gateway ${actionName}ed successfully",
                        )
                    }
                    loadStatus()
                }

                is NetworkResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isActionRunning = false,
                            toastMessage = "Failed to $actionName gateway: ${result.error.message}",
                        )
                    }
                }
            }
        }
    }

    override fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }
}
