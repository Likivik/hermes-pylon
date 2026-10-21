package com.m57.hermescontrol.ui.media

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A remote image the UI renders without knowing which image library is underneath.
 *
 * Shared screens never reference Coil (or any decoder) directly: Android binds this
 * to Coil 3, desktop and web decode the fetched bytes with Skiko. Keeping the
 * library out of the shared surface is what lets the chat cluster live in
 * commonMain at all.
 *
 * [url] must already be authenticated or public — resolving gateway credentials
 * happens before the composable is called.
 */
@Composable
expect fun HermesRemoteImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier,
)
