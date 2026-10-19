package com.m57.hermescontrol.ui.media

import androidx.compose.runtime.Composable

/** Bytes resolved for one image, already carrying what Save/Share need. */
sealed interface ResolvedImage {
    data class Bytes(
        val bytes: ByteArray,
        val mimeType: String,
        val extension: String,
    ) : ResolvedImage

    /** [message] is safe to show to the user. */
    data class Error(
        val message: String,
    ) : ResolvedImage
}

/**
 * The device-side half of the image viewer: fetch, save, share, notify.
 *
 * All four need something shared code cannot name — a `ContentResolver`, a
 * `MediaStore` insert, an `Intent` chooser, a `Toast`. They stay behind one
 * composable-scoped supplier so a screen asks for capabilities, not for a
 * platform type.
 */
interface MediaActions {
    suspend fun resolve(
        model: String,
        fallbackMime: String,
        gatewayPath: String?,
    ): ResolvedImage

    /** True once the bytes are somewhere the user can find them. */
    suspend fun saveImage(
        bytes: ByteArray,
        name: String,
        mimeType: String,
    ): Boolean

    /** True when a share sheet was opened. */
    suspend fun shareImage(
        bytes: ByteArray,
        name: String,
        mimeType: String,
    ): Boolean

    fun toast(message: String)
}

@Composable
expect fun rememberMediaActions(): MediaActions
