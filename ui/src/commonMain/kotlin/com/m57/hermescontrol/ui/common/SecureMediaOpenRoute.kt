package com.m57.hermescontrol.ui.common

import com.m57.hermescontrol.data.model.Attachment
import com.m57.hermescontrol.data.model.AttachmentSource
import com.m57.hermescontrol.data.model.ManagedFileEntry

/** How tapping an attachment or managed file should be handled. */
sealed interface SecureMediaOpenRoute {
    data class Player(val request: SecureGatewayMediaRequest) : SecureMediaOpenRoute

    data object DownloadAndOpen : SecureMediaOpenRoute

    data object OpenLocal : SecureMediaOpenRoute
}

/**
 * Decides between the in-app player and "download then hand to the OS".
 *
 * Pure decision logic, shared by every target: only the *acting* on the route is
 * platform-specific.
 */
fun routeAttachmentOpen(attachment: Attachment): SecureMediaOpenRoute {
    if (attachment.source == AttachmentSource.LOCAL) return SecureMediaOpenRoute.OpenLocal
    val path = attachment.gatewayPath ?: attachment.uri
    val kind =
        mediaKind(attachment.mimeType, path)
            ?: return SecureMediaOpenRoute.DownloadAndOpen
    return SecureMediaOpenRoute.Player(SecureGatewayMediaRequest(path, attachment.name, attachment.mimeType, kind))
}

fun routeManagedFileOpen(entry: ManagedFileEntry): SecureMediaOpenRoute {
    val mimeType =
        entry.mimeType?.takeIf { it.isNotBlank() }
            ?: guessMimeTypeFromName(entry.name).orEmpty()
    val kind = mediaKind(mimeType, entry.path)
    return if (kind != null) {
        SecureMediaOpenRoute.Player(SecureGatewayMediaRequest(entry.path, entry.name, mimeType, kind))
    } else {
        SecureMediaOpenRoute.DownloadAndOpen
    }
}

private fun mediaKind(
    mimeType: String,
    path: String,
): SecureGatewayMediaKind? =
    when {
        mimeType.startsWith("audio/", ignoreCase = true) -> SecureGatewayMediaKind.AUDIO
        mimeType.startsWith("video/", ignoreCase = true) -> SecureGatewayMediaKind.VIDEO
        else ->
            when (path.substringBefore('?').substringAfterLast('.').lowercase()) {
                "mp3", "m4a", "aac", "ogg", "oga", "wav", "flac", "opus" -> SecureGatewayMediaKind.AUDIO
                "mp4", "m4v", "webm", "3gp", "mkv" -> SecureGatewayMediaKind.VIDEO
                else -> null
            }
    }

/**
 * Extension-to-MIME guess for the handful of types this app opens.
 *
 * Replaces `java.net.URLConnection.guessContentTypeFromName`, which does not exist
 * outside the JVM; unknown extensions return null and fall through to the generic
 * download path.
 */
private fun guessMimeTypeFromName(name: String): String? =
    when (name.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "svg" -> "image/svg+xml"
        "pdf" -> "application/pdf"
        "txt", "md", "log" -> "text/plain"
        "json" -> "application/json"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "ogg", "oga" -> "audio/ogg"
        "wav" -> "audio/wav"
        "flac" -> "audio/flac"
        "opus" -> "audio/opus"
        "mp4" -> "video/mp4"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        else -> null
    }
