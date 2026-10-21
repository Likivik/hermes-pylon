package com.m57.hermescontrol.ui.media

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale

/**
 * Renders in-memory image bytes.
 *
 * The image viewer resolves bytes once (authenticated gateway fetch, data URL,
 * or local file) so the same bytes feed the preview, Save and Share. That makes
 * it a bytes-first screen rather than a URL-first one, which [HermesRemoteImage]
 * cannot express.
 */
@Composable
expect fun HermesImageBytes(
    data: ByteArray,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale,
)
