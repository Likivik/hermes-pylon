package com.m57.hermescontrol.ui.media

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

@Composable
actual fun HermesImageBytes(
    data: ByteArray,
    contentDescription: String?,
    modifier: Modifier,
    contentScale: ContentScale,
) {
    AsyncImage(
        model = data,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
    )
}
