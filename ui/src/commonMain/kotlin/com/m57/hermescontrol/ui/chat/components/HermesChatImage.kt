package com.m57.hermescontrol.ui.chat.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * An inline chat image (GIF or still) with a tap target.
 *
 * Android binds this to the Coil-backed thumbnail that can pause and resume an
 * animation; other targets show the first frame through [com.m57.hermescontrol.ui.media.HermesRemoteImage]
 * and keep the click behaviour identical.
 */
@Composable
expect fun HermesChatImage(
    url: String,
    gatewayPath: String?,
    contentDescription: String?,
    isGif: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
)
