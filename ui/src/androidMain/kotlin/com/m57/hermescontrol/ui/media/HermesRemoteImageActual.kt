@file:Suppress("ktlint:standard:filename")

package com.m57.hermescontrol.ui.media

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import coil3.compose.AsyncImage

/**
 * Android renders through Coil, which owns the disk/memory cache and the animated
 * GIF/WebP decoders.
 */
@Composable
actual fun HermesRemoteImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier,
) {
    AsyncImage(
        model = url,
        contentDescription = contentDescription,
        modifier = modifier,
    )
}
