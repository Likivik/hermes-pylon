package com.m57.hermescontrol.ui.chat.input

import androidx.compose.runtime.Composable
import com.m57.hermescontrol.data.model.Attachment

/**
 * The three OS pickers the composer needs, behind one handle.
 *
 * A chat screen cannot open a file picker, a camera or the speech recogniser
 * without host APIs, so the screen keeps only the decisions and this seam owns
 * the launching. The returned callbacks are safe to call from a click handler.
 */
class ChatInputLaunchers(
    /** True while the speech recogniser is on screen, so the mic can show its state. */
    val isListening: Boolean,
    val startSpeechInput: () -> Unit,
    val pickFiles: () -> Unit,
    val takePhoto: () -> Unit,
)

/**
 * @param onSpeechText recognised text; the caller merges it with whatever is
 *   already in the composer, because that policy is shared, not platform-specific.
 * @param onError a localised message for the snackbar, for failures the platform
 *   cannot resolve itself (no recogniser, denied permission, camera failure).
 */
@Composable
expect fun rememberChatInputLaunchers(
    listeningPrompt: String,
    unavailableMessage: String,
    permissionDeniedMessage: String,
    cameraErrorMessage: String,
    onSpeechText: (String) -> Unit,
    onFilesPicked: (List<Attachment>) -> Unit,
    onPhotoTaken: (Attachment) -> Unit,
    onError: (String) -> Unit,
): ChatInputLaunchers
