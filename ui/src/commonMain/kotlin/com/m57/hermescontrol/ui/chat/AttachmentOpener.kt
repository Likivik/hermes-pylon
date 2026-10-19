package com.m57.hermescontrol.ui.chat

import com.m57.hermescontrol.data.model.Attachment
import com.m57.hermescontrol.ui.common.SecureGatewayMediaRequest

/** Why opening an attachment did not work, mapped to a localised message by the host. */
enum class AttachmentOpenError {
    OPEN,
    NOT_FOUND,
    ACCESS_DENIED,
    TOO_LARGE,
    SESSION_EXPIRED,
    MISSING_GATEWAY_PATH,
}

/** What the platform did with a tapped attachment. */
sealed interface AttachmentOpenOutcome {
    /** The attachment is audio/video and should play in the in-app player. */
    data class PlayMedia(val request: SecureGatewayMediaRequest) : AttachmentOpenOutcome

    /** The platform handed it to a viewer. */
    data object Handled : AttachmentOpenOutcome

    data class Failed(
        val error: AttachmentOpenError,
        val name: String,
    ) : AttachmentOpenOutcome
}

/**
 * Opens a tapped attachment with whatever the host has: on Android a FileProvider
 * intent, elsewhere nothing yet. [onProgress] reports the gateway path being
 * downloaded so the UI can show a spinner, and is called with null when done.
 */
fun interface AttachmentOpener {
    suspend fun open(
        attachment: Attachment,
        onProgress: (String?) -> Unit,
    ): AttachmentOpenOutcome
}
