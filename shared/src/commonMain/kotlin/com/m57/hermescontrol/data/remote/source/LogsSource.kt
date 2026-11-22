package com.m57.hermescontrol.data.remote.source

import com.m57.hermescontrol.data.model.LogResponse
import com.m57.hermescontrol.data.model.ManagedFileActionResponse
import com.m57.hermescontrol.data.remote.GatewayResponse

/**
 * What the logs screen needs from the gateway: 1 calls of the 150 it offers.
 *
 * Replaces the `SessionsGateway` wrapper added in #257. The wrapper duplicated signatures for no gain; this names
 * only what this screen calls, and `LogsSourceOf` is the production adapter.
 */
interface LogsSource {
    suspend fun uploadManagedFileStream(
        path: String,
        overwrite: Boolean,
        fileName: String,
        content: ByteArray,
        mimeType: String? = null,
    ): GatewayResponse<ManagedFileActionResponse>

    suspend fun getLogs(
        file: String = "agent",
        lines: Int = 100,
        level: String = "ALL",
        component: String = "all",
    ): GatewayResponse<LogResponse>
}
