package com.m57.hermescontrol.data.model

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/**
 * Represents a file attached to a chat message.
 *
 * @param uri Content URI or file path to the attachment. Gateway-sourced
 *   attachments use the gateway-host path, never a credential-bearing URL.
 * @param name Display name of the file
 * @param mimeType MIME type (e.g., "image/jpeg", "application/pdf")
 * @param size File size in bytes (0 when unknown, e.g. gateway-hosted files)
 * @param gatewayPath When non-null, the absolute path for a file that lives on
 *   the gateway host (agent-delivered `MEDIA:` directive).
 *   Null for locally-picked attachments.
 * @param source Where the attachment originated.
 */
@Serializable
data class Attachment(
    val uri: String,
    val name: String,
    val mimeType: String,
    val size: Long = 0,
    val gatewayPath: String? = null,
    val source: AttachmentSource = AttachmentSource.LOCAL,
) {
    val isImage: Boolean
        get() = mimeType.startsWith("image/")

    val isGif: Boolean
        get() =
            mimeType.equals("image/gif", ignoreCase = true) ||
                fileExtension == "gif" ||
                uri.contains(".gif", ignoreCase = true) ||
                uri.startsWith("data:image/gif", ignoreCase = true)

    val isGateway: Boolean
        get() = source == AttachmentSource.GATEWAY

    val formattedSize: String
        get() =
            when {
                size <= 0 -> "—"
                size < 1024 -> "$size B"
                size < 1024 * 1024 -> "${size / 1024} KB"
                else -> {
                    // One decimal, locale-independent: String.format is JVM-only.
                    val megabytes = size.toDouble() / (1024 * 1024)
                    val rounded = (megabytes * 10).roundToInt() / 10.0
                    "$rounded MB"
                }
            }

    val fileExtension: String
        get() = name.substringAfterLast('.', "").lowercase()
}

/** Origin of an [Attachment]. */
@Serializable
enum class AttachmentSource {
    /** Picked by the user on this device (content URI / local path). */
    LOCAL,

    /** Delivered by the agent via a gateway-hosted `MEDIA:` directive. */
    GATEWAY,
}
