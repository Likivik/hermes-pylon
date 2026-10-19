package com.m57.hermescontrol.ui.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import com.m57.hermescontrol.ui.common.SecureGatewayMediaRequest

/** Snapshot of one playback session. */
data class MediaPlaybackState(
    val prepared: Boolean = false,
    val playing: Boolean = false,
    val positionMillis: Int = 0,
    val durationMillis: Int = 0,
    val failed: Boolean = false,
)

/**
 * Transport for one piece of media.
 *
 * The controls (slider, play/pause, elapsed time, error text) are identical on
 * every target, so they live in commonMain against this interface; only the
 * player object and the drawing surface are platform code. Keeping the state in
 * a [State] rather than callbacks means the shared UI recomposes on position
 * ticks without knowing whether a `MediaPlayer` or an HTML `<video>` produced
 * them.
 */
interface MediaPlaybackController {
    val state: State<MediaPlaybackState>

    fun play()

    fun pause()

    fun seekTo(positionMillis: Int)

    fun release()
}

/**
 * Prepares playback for [request]. The returned controller owns its source and
 * must be [MediaPlaybackController.release]d; implementations release on
 * composition exit.
 */
@Composable
expect fun rememberMediaPlayback(request: SecureGatewayMediaRequest): MediaPlaybackController

/** Draws video frames for [controller]. No-op for audio-only media. */
@Composable
expect fun MediaSurface(
    controller: MediaPlaybackController,
    contentDescription: String?,
    modifier: Modifier,
)

/** `m:ss`, truncated — never shown for negative input. */
fun formatMediaTime(milliseconds: Int): String {
    val totalSeconds = milliseconds.coerceAtLeast(0) / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${if (seconds < 10) "0$seconds" else "$seconds"}"
}
