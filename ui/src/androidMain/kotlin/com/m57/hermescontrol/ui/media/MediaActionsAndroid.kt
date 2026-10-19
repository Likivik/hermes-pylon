package com.m57.hermescontrol.ui.media

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.m57.hermescontrol.ui.chat.ImageBytesResolver
import com.m57.hermescontrol.ui.chat.MediaImageStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
actual fun rememberMediaActions(): MediaActions {
    val context = LocalContext.current

    // API < 29 still needs WRITE_EXTERNAL_STORAGE to land a file in Downloads.
    // The launcher is created here because permission requests can only be
    // launched from a composition; the deferred lets `saveImage` suspend until
    // the answer arrives instead of splitting the flow across callbacks.
    val pending = remember { PendingPermission() }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            pending.complete(granted)
        }

    return remember(context, launcher) {
        object : MediaActions {
            override suspend fun resolve(
                model: String,
                fallbackMime: String,
                gatewayPath: String?,
            ): ResolvedImage =
                when (
                    val result =
                        ImageBytesResolver.resolve(
                            context = context,
                            model = model,
                            fallbackMime = fallbackMime,
                            gatewayPath = gatewayPath,
                        )
                ) {
                    is ImageBytesResolver.Result.Bytes ->
                        ResolvedImage.Bytes(result.bytes, result.mimeType, result.extension)

                    is ImageBytesResolver.Result.Error -> ResolvedImage.Error(result.message)
                }

            override suspend fun saveImage(
                bytes: ByteArray,
                name: String,
                mimeType: String,
            ): Boolean {
                if (requiresLegacyStoragePermission(context) && !hasStoragePermission(context)) {
                    val granted = pending.await { launcher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) }
                    if (!granted) return false
                }
                return MediaImageStore.saveToDownloads(context, bytes, name, mimeType) != null
            }

            override suspend fun shareImage(
                bytes: ByteArray,
                name: String,
                mimeType: String,
            ): Boolean {
                val intent = MediaImageStore.buildShareIntent(context, bytes, name, mimeType) ?: return false
                withContext(Dispatchers.Main) {
                    context.startActivity(Intent.createChooser(intent, name))
                }
                return true
            }

            override fun toast(message: String) {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/** One outstanding permission request at a time; the viewer is modal anyway. */
internal class PendingPermission {
    private var deferred: CompletableDeferred<Boolean>? = null

    suspend fun await(launch: () -> Unit): Boolean {
        deferred?.cancel()
        val next = CompletableDeferred<Boolean>()
        deferred = next
        launch()
        return next.await()
    }

    fun complete(granted: Boolean) {
        deferred?.complete(granted)
        deferred = null
    }
}

internal fun requiresLegacyStoragePermission(context: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

internal fun hasStoragePermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
    ) == PackageManager.PERMISSION_GRANTED
