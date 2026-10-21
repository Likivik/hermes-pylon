package com.m57.hermescontrol.ui.media

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.m57.hermescontrol.ui.common.SecureGatewayMediaRequest

/**
 * No player on this target yet.
 *
 * Desktop and web have no equivalent of `MediaPlayer` in the dependency set, and
 * inventing a decoder here would be a worse outcome than an honest failure: the
 * shared controls render the error state, so the user sees "could not play"
 * rather than a silent blank area. Adding Skiko video or an HTML `<video>` is a
 * target-local change behind this seam.
 */
@Composable
actual fun rememberMediaPlayback(request: SecureGatewayMediaRequest): MediaPlaybackController =
    remember(request) {
        object : MediaPlaybackController {
            override val state: MutableState<MediaPlaybackState> =
                mutableStateOf(MediaPlaybackState(failed = true))

            override fun play() = Unit

            override fun pause() = Unit

            override fun seekTo(positionMillis: Int) = Unit

            override fun release() = Unit
        }
    }

@Composable
actual fun MediaSurface(
    controller: MediaPlaybackController,
    contentDescription: String?,
    modifier: Modifier,
) {
    Box(modifier = modifier)
}
