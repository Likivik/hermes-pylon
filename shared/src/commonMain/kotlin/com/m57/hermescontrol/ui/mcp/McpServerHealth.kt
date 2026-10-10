package com.m57.hermescontrol.ui.mcp

import com.m57.hermescontrol.data.model.McpServerTestResponse
import com.m57.hermescontrol.data.model.McpServerToolInfo
import kotlin.math.ceil
import kotlin.math.roundToInt

object McpTokenEstimator {
    fun estimateTokens(tools: List<McpServerToolInfo>): Int? {
        val schemaSizes = tools.mapNotNull { it.schemaChars?.takeIf { chars -> chars > 0 } }
        return schemaSizes.takeIf { it.isNotEmpty() }?.sumOf { ceil(it / 4.0).toInt() }
    }

    fun formatTokenOverhead(
        toolCount: Int,
        tokenEstimate: Int?,
    ): String {
        val tools = if (toolCount == 1) "1 tool" else "$toolCount tools"
        if (tokenEstimate == null) return tools
        val tokens =
            if (tokenEstimate >= 1000) {
                oneDecimalK(tokenEstimate)
            } else {
                tokenEstimate.toString()
            }
        return "$tools • ~$tokens tokens"
    }
}

enum class McpHealthStatus {
    HEALTHY,
    AUTH_REQUIRED,
    ERROR,
    TESTING,
    UNKNOWN,
    ;

    companion object {
        fun resolve(
            isTesting: Boolean,
            testResult: McpServerTestResponse?,
            serverStatus: String?,
            serverError: String?,
        ): McpHealthStatus {
            if (isTesting) return TESTING
            if (testResult?.ok == true) return HEALTHY
            val error = testResult?.error ?: serverError
            if (!error.isNullOrBlank()) {
                val normalized = error.lowercase()
                return if (
                    normalized.contains("oauth") || normalized.contains("auth") || normalized.contains("token")
                ) {
                    AUTH_REQUIRED
                } else {
                    ERROR
                }
            }
            return when (serverStatus?.lowercase()) {
                "running", "ok", "connected", "healthy" -> HEALTHY
                "error", "failed" -> ERROR
                else -> UNKNOWN
            }
        }
    }
}

/** `3200 -> "3.2k"`, rounding half-up like `String.format(Locale.US, "%.1fk", ...)` did.
 *
 * Multiplatform replacement for `String.format`, which is JVM-only. */
private fun oneDecimalK(tokenEstimate: Int): String {
    val tenths = (tokenEstimate / 100.0).roundToInt()
    return "${tenths / 10}.${tenths % 10}k"
}
