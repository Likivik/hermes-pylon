@file:Suppress("ktlint:standard:filename")

package com.m57.hermescontrol.ui.chat.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Android keeps the animated Coil thumbnail: it can pause and resume a GIF. */
@Composable
actual fun HermesChatImage(
    url: String,
    gatewayPath: String?,
    contentDescription: String?,
    isGif: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    GifImageThumbnail(
        model = url,
        gatewayPath = gatewayPath,
        contentDescription = contentDescription,
        isGif = isGif,
        onClick = onClick,
        modifier = modifier,
    )
}
