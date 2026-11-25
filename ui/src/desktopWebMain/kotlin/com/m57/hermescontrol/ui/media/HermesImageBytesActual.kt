package com.m57.hermescontrol.ui.media

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.skia.Image as SkiaImage

/** Decoded with Skiko: this target ships no image-loading library. */
@Composable
actual fun HermesImageBytes(
    data: ByteArray,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale,
) {
    val bitmap = remember(data) { runCatching { SkiaImage.makeFromEncoded(data).toComposeImageBitmap() }.getOrNull() }
    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
        )
    }
}
