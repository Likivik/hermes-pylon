@file:Suppress("ktlint:standard:filename")

package com.m57.hermescontrol.ui.chat.input

import androidx.compose.runtime.Composable
import com.m57.hermescontrol.data.model.Attachment

/**
 * Desktop and web have no pickers yet, so every entry point is a no-op rather than
 * a crash: the composer buttons stay visible and simply do nothing.
 */
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
): ChatInputLaunchers =
    ChatInputLaunchers(
        isListening = false,
        startSpeechInput = {},
        pickFiles = {},
        takePhoto = {},
    )
