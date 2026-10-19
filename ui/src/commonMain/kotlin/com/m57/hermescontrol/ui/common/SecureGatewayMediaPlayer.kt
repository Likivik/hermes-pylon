package com.m57.hermescontrol.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.m57.hermescontrol.generated.resources.Res
import com.m57.hermescontrol.generated.resources.media_player_close
import com.m57.hermescontrol.generated.resources.media_player_error
import com.m57.hermescontrol.generated.resources.media_player_pause
import com.m57.hermescontrol.generated.resources.media_player_play
import com.m57.hermescontrol.generated.resources.media_player_seek
import com.m57.hermescontrol.theme.Spacing
import com.m57.hermescontrol.ui.icons.HermesIcon
import com.m57.hermescontrol.ui.icons.HermesSymbol
import com.m57.hermescontrol.ui.media.MediaSurface
import com.m57.hermescontrol.ui.media.formatMediaTime
import com.m57.hermescontrol.ui.media.rememberMediaPlayback
import org.jetbrains.compose.resources.stringResource

/**
 * Plays gateway-hosted audio and video.
 *
 * The source is fetched with the session's credentials by the platform player, so
 * a gateway path never becomes a public URL. Everything visible here — title bar,
 * slider, elapsed time, play/pause, error state — is shared; the player object and
 * the video surface come from [rememberMediaPlayback] and [MediaSurface].
 */
@Composable
fun SecureGatewayMediaPlayer(
    request: SecureGatewayMediaRequest,
    onClose: () -> Unit,
) {
    val controller = rememberMediaPlayback(request)
    val state by controller.state
    var draggingPosition by remember(request) { mutableIntStateOf(-1) }
    val seekDescription = stringResource(Res.string.media_player_seek)

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(modifier = Modifier.fillMaxWidth().padding(Spacing.md)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = request.title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onClose) {
                        HermesSymbol(
                            icon = HermesIcon.Close,
                            contentDescription = stringResource(Res.string.media_player_close),
                        )
                    }
                }

                if (request.isVideo) {
                    MediaSurface(
                        controller = controller,
                        contentDescription = request.title,
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                    )
                }

                if (!state.prepared && !state.failed) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                if (state.failed) {
                    Text(
                        text = stringResource(Res.string.media_player_error),
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                val duration = state.durationMillis
                val shownPosition = if (draggingPosition >= 0) draggingPosition else state.positionMillis
                Slider(
                    value = shownPosition.toFloat().coerceIn(0f, duration.coerceAtLeast(1).toFloat()),
                    onValueChange = { draggingPosition = it.toInt() },
                    onValueChangeFinished = {
                        if (state.prepared && draggingPosition >= 0) {
                            controller.seekTo(draggingPosition)
                        }
                        draggingPosition = -1
                    },
                    valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
                    enabled = state.prepared && !state.failed && duration > 0,
                    modifier =
                        Modifier.semantics {
                            contentDescription = seekDescription
                        },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${formatMediaTime(shownPosition)} / ${formatMediaTime(duration)}")
                    IconButton(
                        onClick = { if (state.playing) controller.pause() else controller.play() },
                        enabled = state.prepared && !state.failed,
                    ) {
                        HermesSymbol(
                            icon = if (state.playing) HermesIcon.Pause else HermesIcon.PlayArrow,
                            contentDescription =
                                stringResource(
                                    if (state.playing) Res.string.media_player_pause else Res.string.media_player_play,
                                ),
                        )
                    }
                }
            }
        }
    }
}
