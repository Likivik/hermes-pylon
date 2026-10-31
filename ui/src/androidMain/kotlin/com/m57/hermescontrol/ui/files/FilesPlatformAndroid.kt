package com.m57.hermescontrol.ui.files

import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Storage Access Framework for picking, a FileProvider for opening — exactly
 * what the screen did while it lived in the Android source set.
 */
@Composable
actual fun rememberFilesPlatform(): FilesPlatform {
    val context = LocalContext.current
    // The launcher can only be created in composition, so the pending callbacks
    // are held here and consumed by whichever pick() the user triggers.
    val pending = remember { PendingPick() }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            val picked = pending
            if (uri == null) {
                picked.onError?.invoke(null)
                picked.clear()
                return@rememberLauncherForActivityResult
            }
            val name = uri.lastPathSegment?.substringAfterLast("/")?.takeIf { it.isNotBlank() } ?: "upload.bin"
            val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }
            picked.clear()
            when {
                bytes.isFailure -> picked.onError?.invoke(bytes.exceptionOrNull()?.message)
                bytes.getOrNull() == null -> picked.onError?.invoke(null)
                else -> picked.onPicked?.invoke(PickedFile(name, mime, bytes.getOrThrow()!!))
            }
        }
    return remember(context, launcher) {
        object : FilesPlatform {
            override val canPickFiles = true
            override val canOpenDownloads = true

            override fun pickFile(
                mimeType: String,
                onPicked: (PickedFile) -> Unit,
                onError: (String?) -> Unit,
            ) {
                pending.onPicked = onPicked
                pending.onError = onError
                launcher.launch(mimeType.ifBlank { "*/*" })
            }

            override fun stagingDirectory(): String = File(context.cacheDir, "gateway_media").absolutePath

            override suspend fun openDownloaded(
                cachePath: String,
                name: String,
                mimeType: String,
            ): Result<Unit> =
                withContext(Dispatchers.IO) {
                    runCatching {
                        val uri: Uri =
                            FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                File(cachePath),
                            )
                        val intent =
                            Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, mimeType)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                        context.startActivity(intent)
                    }
                }

            override fun formatSize(bytes: Long): String = Formatter.formatShortFileSize(context, bytes)

            override fun formatDateTime(epochMillis: Long): Pair<String, String> {
                val date = java.util.Date(epochMillis)
                return DateFormat.getMediumDateFormat(context).format(date) to
                    DateFormat.getTimeFormat(context).format(date)
            }
        }
    }
}

private class PendingPick {
    var onPicked: ((PickedFile) -> Unit)? = null
    var onError: ((String?) -> Unit)? = null

    fun clear() {
        onPicked = null
        onError = null
    }
}
