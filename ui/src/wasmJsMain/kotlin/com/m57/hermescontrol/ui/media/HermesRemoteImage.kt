package com.m57.hermescontrol.ui.media

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.m57.hermescontrol.data.remote.createHermesHttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import org.jetbrains.skia.Image as SkiaImage

/**
 * The web target decodes with Skiko compiled to wasm, after fetching the bytes.
 *
 * Bytes are fetched with the shared Ktor client and decoded by Skiko, so this
 * target needs no image-loading library at all. Animated formats (GIF/WebP) show
 * their first frame; Android remains the only target with real animation.
 */
@Composable
actual fun HermesRemoteImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier,
) {
    val bitmap: ImageBitmap? by produceState<ImageBitmap?>(initialValue = null, url) {
        value =
            runCatching {
                val bytes = createHermesHttpClient().get(url).bodyAsBytes()
                SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
            }.getOrNull()
    }
    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    }
}
