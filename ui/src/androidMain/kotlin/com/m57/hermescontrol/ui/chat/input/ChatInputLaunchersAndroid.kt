package com.m57.hermescontrol.ui.chat.input

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.m57.hermescontrol.data.model.Attachment
import com.m57.hermescontrol.ui.chat.SpeechInputHelper
import com.m57.hermescontrol.util.HermesLog
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
actual fun rememberChatInputLaunchers(
    listeningPrompt: String,
    unavailableMessage: String,
    permissionDeniedMessage: String,
    cameraErrorMessage: String,
    onSpeechText: (String) -> Unit,
    onFilesPicked: (List<Attachment>) -> Unit,
    onPhotoTaken: (Attachment) -> Unit,
    onError: (String) -> Unit,
): ChatInputLaunchers {
    val context = LocalContext.current
    var isListening by remember { mutableStateOf(false) }

    val speechLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            isListening = false
            if (result.resultCode == Activity.RESULT_OK) {
                val spokenText =
                    result.data
                        ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                        ?.firstOrNull()
                        .orEmpty()
                if (spokenText.isNotBlank()) onSpeechText(spokenText)
            }
        }

    val micPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            if (!granted) {
                onError(permissionDeniedMessage)
            } else if (!SpeechInputHelper.isSpeechInputAvailable(context)) {
                onError(unavailableMessage)
            } else {
                val intent =
                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                        )
                        putExtra(RecognizerIntent.EXTRA_PROMPT, listeningPrompt)
                    }
                isListening = true
                try {
                    speechLauncher.launch(intent)
                } catch (_: ActivityNotFoundException) {
                    isListening = false
                    onError(unavailableMessage)
                }
            }
        }

    val filePickerLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.GetMultipleContents(),
        ) { uris: List<Uri> -> onFilesPicked(uris.mapNotNull { describe(context, it) }) }

    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.TakePicture(),
        ) { success ->
            val uri = pendingCameraUri
            pendingCameraUri = null
            if (success && uri != null) {
                try {
                    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                    val size =
                        context.contentResolver.openInputStream(uri)?.use { it.available().toLong() } ?: 0L
                    onPhotoTaken(
                        Attachment(
                            uri = uri.toString(),
                            name = "photo_" + stamp + ".jpg",
                            mimeType = "image/jpeg",
                            size = size,
                        ),
                    )
                } catch (e: Exception) {
                    HermesLog.e("ChatInputLaunchers", "Camera capture failed (" + e::class.simpleName + ")")
                    onError(cameraErrorMessage)
                }
            }
        }

    return ChatInputLaunchers(
        isListening = isListening,
        startSpeechInput = {
            // The launcher answers immediately when the permission is already held.
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        },
        pickFiles = { filePickerLauncher.launch("*/*") },
        takePhoto = {
            try {
                val dir = File(context.cacheDir, "camera").apply { mkdirs() }
                val file = File(dir, "capture_" + System.currentTimeMillis() + ".jpg")
                val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
                pendingCameraUri = uri
                cameraLauncher.launch(uri)
            } catch (e: Exception) {
                HermesLog.e("ChatInputLaunchers", "Camera launch failed (" + e::class.simpleName + ")")
                onError(cameraErrorMessage)
            }
        },
    )
}

/** Reads the columns the picker exposes instead of guessing a name from the Uri. */
private fun describe(
    context: android.content.Context,
    uri: Uri,
): Attachment? =
    try {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst()) return null
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                Attachment(
                    uri = uri.toString(),
                    name =
                        if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                            cursor.getString(nameIndex)
                        } else {
                            "file"
                        },
                    mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream",
                    size =
                        if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                            cursor.getLong(sizeIndex)
                        } else {
                            0L
                        },
                )
            }
    } catch (e: Exception) {
        HermesLog.e("ChatInputLaunchers", "Attachment describe failed (" + e::class.simpleName + ")")
        null
    }
