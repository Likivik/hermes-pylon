package com.m57.hermescontrol.ui.files

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.swing.JFileChooser

/** Desktop: a file chooser for picking, `Desktop.open` for opening downloads. */
@Composable
actual fun rememberFilesPlatform(): FilesPlatform =
    remember {
        object : FilesPlatform {
            override val canPickFiles = true
            override val canOpenDownloads = Desktop.isDesktopSupported()

            override fun pickFile(
                mimeType: String,
                onPicked: (PickedFile) -> Unit,
                onError: (String?) -> Unit,
            ) {
                val chooser = JFileChooser()
                if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) {
                    onError(null)
                    return
                }
                val file = chooser.selectedFile
                runCatching { PickedFile(file.name, mimeType.ifBlank { "application/octet-stream" }, file.readBytes()) }
                    .onSuccess(onPicked)
                    .onFailure { onError(it.message) }
            }

            override fun stagingDirectory(): String =
                File(System.getProperty("java.io.tmpdir"), "hermes-gateway-media")
                    .apply { mkdirs() }
                    .absolutePath

            override suspend fun openDownloaded(
                cachePath: String,
                name: String,
                mimeType: String,
            ): Result<Unit> =
                withContext(Dispatchers.IO) {
                    runCatching {
                        check(canOpenDownloads) { "desktop integration unavailable" }
                        Desktop.getDesktop().open(File(cachePath))
                    }
                }

            override fun formatSize(bytes: Long): String = humanSize(bytes)

            override fun formatDateTime(epochMillis: Long): Pair<String, String> {
                val date = Date(epochMillis)
                return SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(date) to
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
            }
        }
    }

internal fun humanSize(bytes: Long): String {
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
