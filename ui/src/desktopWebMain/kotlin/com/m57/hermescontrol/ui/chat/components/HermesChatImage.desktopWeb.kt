package com.m57.hermescontrol.ui.chat.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.m57.hermescontrol.ui.media.HermesRemoteImage

/**
 * Desktop decodes with Skiko.
 *
 * Skiko decodes the first frame only, so an animated GIF renders as a still here;
 * taps still open the same viewer.
 */
@Composable
actual fun HermesChatImage(
    url: String,
    gatewayPath: String?,
    contentDescription: String?,
    isGif: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick),
    ) {
        HermesRemoteImage(
            url = gatewayPath ?: url,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
