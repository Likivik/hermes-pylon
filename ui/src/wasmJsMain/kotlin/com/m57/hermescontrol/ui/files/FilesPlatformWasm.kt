package com.m57.hermescontrol.ui.files

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@JsFun("(ms) => new Date(ms).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' })")
private external fun jsDateString(ms: Double): String

@JsFun("(ms) => new Date(ms).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })")
private external fun jsTimeString(ms: Double): String

/**
 * The browser cannot do either half of this yet: there is no file-system picker
 * bound to the gateway upload path, and no way to hand a cached file to an
 * external viewer. Both say so plainly instead of failing silently.
 */
@Composable
actual fun rememberFilesPlatform(): FilesPlatform =
    remember {
        object : FilesPlatform {
            override val canPickFiles = false
            override val canOpenDownloads = false

            override fun pickFile(
                mimeType: String,
                onPicked: (PickedFile) -> Unit,
                onError: (String?) -> Unit,
            ) {
                onError("Uploading files is not supported in the browser build yet")
            }

            override fun stagingDirectory(): String? = null

            override suspend fun openDownloaded(
                cachePath: String,
                name: String,
                mimeType: String,
            ): Result<Unit> =
                Result.failure(
                    UnsupportedOperationException("Opening downloads is not supported in the browser build yet"),
                )

            override fun formatSize(bytes: Long): String {
                if (bytes < 1024) return "$bytes B"
                val units = listOf("KB", "MB", "GB", "TB")
                var value = bytes.toDouble() / 1024.0
                var unit = 0
                while (value >= 1024.0 && unit < units.lastIndex) {
                    value /= 1024.0
                    unit++
                }
                return "${(value * 10).toLong() / 10.0} ${units[unit]}"
            }

            override fun formatDateTime(epochMillis: Long): Pair<String, String> =
                jsDateString(epochMillis.toDouble()) to jsTimeString(epochMillis.toDouble())
        }
    }
