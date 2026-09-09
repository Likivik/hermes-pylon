package com.m57.hermescontrol.ui.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m57.hermescontrol.data.remote.ApiClient
import com.m57.hermescontrol.data.remote.safeApiCall
import com.m57.hermescontrol.ui.common.ToastHost
import com.m57.hermescontrol.ui.common.safeLaunchLoad
import io.kseongbin.stacktrace.CrashLogger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Server-side log filters, mirroring the desktop dashboard's LogsPage
 * (`GET /api/logs?file=&lines=&level=&component=`).
 */
data class LogsFilters(
    val file: String = "agent",
    val level: String = "ALL",
    val component: String = "all",
    val lines: Int = 100,
)

data class LogsUiState(
    val isLoading: Boolean = false,
    val logs: List<String> = emptyList(),
    val errorMessage: String? = null,
    val toastMessage: String? = null,
    val filters: LogsFilters = LogsFilters(),
)

class LogsViewModel(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) :
    ViewModel(),
        ToastHost {
    private val _uiState = MutableStateFlow(LogsUiState())
    val uiState: StateFlow<LogsUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var autoRefreshJob: Job? = null

    fun loadLogs() {
        val filters = _uiState.value.filters
        loadJob =
            safeLaunchLoad(
                currentJob = loadJob,
                ioDispatcher = ioDispatcher,
                apiCall = {
                    safeApiCall {
                        ApiClient.hermesApi.getLogs(
                            file = filters.file,
                            lines = filters.lines,
                            level = filters.level,
                            component = filters.component,
                        )
                    }
                },
                onStart = { _uiState.update { it.copy(isLoading = true, errorMessage = null) } },
                onSuccess = { data ->
                    val logsList = data.lines ?: data.logs ?: emptyList()
                    _uiState.update { it.copy(isLoading = false, logs = logsList) }
                },
                onError = { errorMsg ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Failed to load logs: $errorMsg",
                        )
                    }
                },
            )
    }

    /** Update the server-side filters and immediately reload. */
    fun setFilters(filters: LogsFilters) {
        if (filters == _uiState.value.filters) return

        _uiState.update { it.copy(filters = filters) }
        loadJob?.cancel()
        loadJob = null
        loadLogs()
    }

    /** Start auto-refreshing logs every 5 seconds. */
    fun startAutoRefresh() {
        if (autoRefreshJob?.isActive == true) return
        autoRefreshJob =
            viewModelScope.launch {
                while (isActive) {
                    delay(5_000)
                    loadLogs()
                }
            }
    }

    /** Stop auto-refreshing logs. */
    fun stopAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    override fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    /**
     * Likivik patch: upload CrashWatcher's crash/ANR log files to the gateway's
     * managed files (path `logs/`) so they can be diagnosed off-device without
     * logcat/ADB. User-initiated from the Logs screen.
     */
    fun uploadAppLogs() {
        viewModelScope.launch {
            var uploaded = 0
            var failed = false
            withContext(ioDispatcher) {
                val dir = CrashLogger.getLogDirectory()
                val files = dir?.listFiles()?.filter { it.isFile }?.sortedBy { it.lastModified() }
                    ?: emptyList()
                if (files.isEmpty()) {
                    failed = true
                } else {
                    for (file in files) {
                        val result =
                            safeApiCall {
                                val pathBody = "logs/${file.name}".toRequestBody("text/plain".toMediaTypeOrNull())
                                val overwriteBody = "true".toRequestBody("text/plain".toMediaTypeOrNull())
                                val part = MultipartBody.Part.createFormData(
                                    "file",
                                    file.name,
                                    file.asRequestBody("text/plain".toMediaTypeOrNull()),
                                )
                                ApiClient.hermesApi.uploadManagedFileStream(pathBody, overwriteBody, part)
                            }
                        if (result is com.m57.hermescontrol.data.remote.NetworkResult.Success) uploaded++ else failed = true
                    }
                }
            }
            val msg =
                when {
                    uploaded == 0 && failed -> "No app logs found to upload"
                    failed -> "Uploaded $uploaded log(s), some failed"
                    else -> "Uploaded $uploaded app log(s) to Files"
                }
            _uiState.update { it.copy(toastMessage = msg) }
        }
    }
}
