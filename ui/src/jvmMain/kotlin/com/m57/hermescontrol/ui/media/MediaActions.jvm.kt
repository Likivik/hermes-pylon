package com.m57.hermescontrol.ui.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.m57.hermescontrol.data.remote.createHermesHttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes

@Composable
actual fun rememberMediaActions(): MediaActions =
    remember {
        object : MediaActions {
            override suspend fun resolve(
                model: String,
                fallbackMime: String,
                gatewayPath: String?,
            ): ResolvedImage {
                if (gatewayPath != null) return ResolvedImage.Error(GATEWAY_UNSUPPORTED)
                return runCatching {
                    when {
                        model.startsWith("data:") -> decodeDataUrl(model, fallbackMime)
                        model.startsWith("http://") || model.startsWith("https://") ->
                            fetch(model, fallbackMime)
                        else -> ResolvedImage.Error("Unsupported image source")
                    }
                }.getOrElse { ResolvedImage.Error(it.message ?: "Failed to load image") }
            }

            override suspend fun saveImage(
                bytes: ByteArray,
                name: String,
                mimeType: String,
            ): Boolean {
                return runCatching {
                    val dir =
                        java.io.File(System.getProperty("user.home"), "Downloads").apply { mkdirs() }
                    java.io.File(dir, name).writeBytes(bytes)
                    true
                }.getOrDefault(false)
            }

            override suspend fun shareImage(
                bytes: ByteArray,
                name: String,
                mimeType: String,
            ): Boolean = false

            override fun toast(message: String) {
                println("hermes: $message")
            }
        }
    }

private const val GATEWAY_UNSUPPORTED =
    "Authenticated gateway images are not available on this target yet"

private suspend fun fetch(
    model: String,
    fallbackMime: String,
): ResolvedImage {
    val bytes = createHermesHttpClient().get(model).bodyAsBytes()
    val mime = fallbackMime.ifBlank { "image/png" }
    return ResolvedImage.Bytes(bytes, mime, extensionForMime(mime))
}

/**
 * `data:image/png;base64,AAA...` - the payload after the comma is base64,
 * decoded by the shared [base64Decode] so both targets behave identically.
 */
private fun decodeDataUrl(
    model: String,
    fallbackMime: String,
): ResolvedImage {
    val comma = model.indexOf(',')
    if (comma < 0) return ResolvedImage.Error("Malformed data URL")
    val header = model.substring(0, comma)
    val mime =
        header
            .removePrefix("data:")
            .substringBefore(';')
            .ifBlank { fallbackMime.ifBlank { "image/png" } }
    val decoded =
        runCatching { base64Decode(model.substring(comma + 1)) }.getOrNull()
            ?: return ResolvedImage.Error("Malformed data URL")
    return ResolvedImage.Bytes(decoded, mime, extensionForMime(mime))
}

private fun extensionForMime(mime: String): String =
    when (mime.lowercase()) {
        "image/jpeg", "image/jpg" -> "jpg"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        "image/heic" -> "heic"
        "image/avif" -> "avif"
        else -> "png"
    }
