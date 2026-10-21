package com.m57.hermescontrol.ui.media

import android.media.MediaPlayer
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.m57.hermescontrol.data.remote.GatewayFileClient
import com.m57.hermescontrol.ui.common.GatewayMediaDataSource
import com.m57.hermescontrol.ui.common.SecureGatewayMediaRequest
import com.m57.hermescontrol.util.ioDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Android playback over `MediaPlayer`, fed by the seekable gateway data source.
 *
 * Everything the shared UI sees is [MediaPlaybackState]; the `MediaPlayer`, the
 * `SurfaceView` and the lifecycle pause all stay here. The source is opened on
 * the IO dispatcher and polled for staleness — an expired gateway session stops
 * playback instead of rendering garbage frames.
 */
@Composable
actual fun rememberMediaPlayback(request: SecureGatewayMediaRequest): MediaPlaybackController {
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember(request) { AndroidMediaPlaybackController() }

    LaunchedEffect(request, controller) {
        try {
            controller.open(request.path)
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            controller.fail()
        }
    }

    DisposableEffect(controller, lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP && controller.state.value.playing) {
                    controller.pause()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller.release()
        }
    }

    LaunchedEffect(request, controller, lifecycleOwner.lifecycle.currentState) {
        val source = controller.dataSource ?: return@LaunchedEffect
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            controller.playWhenReady()
        }
        monitorSecureMediaBoundary(
            dispatcher = ioDispatcher,
            pollIntervalMillis = 250,
            isCurrent = source::isCurrent,
            onCurrent = { controller.syncPosition() },
            onStale = { controller.fail() },
        )
    }

    return controller
}

/** Body of the controller: one `MediaPlayer` per request. */
private class AndroidMediaPlaybackController : MediaPlaybackController {
    private val mutableState = mutableStateOf(MediaPlaybackState())
    override val state: MutableState<MediaPlaybackState> = mutableState
    private val current: MediaPlaybackState get() = mutableState.value

    private var player: MediaPlayer? = null
    private var pendingSurface: SurfaceHolder? = null
    var dataSource: GatewayMediaDataSource? = null
        private set

    suspend fun open(path: String) {
        val source = GatewayMediaDataSource(GatewayFileClient.openSeekable(path))
        dataSource = source
        val created =
            MediaPlayer().apply {
                setDataSource(source)
                setOnPreparedListener {
                    mutableState.value =
                        current.copy(
                            prepared = true,
                            durationMillis = it.duration.coerceAtLeast(0),
                        )
                }
                setOnCompletionListener {
                    mutableState.value =
                        current.copy(playing = false, positionMillis = current.durationMillis)
                }
                setOnErrorListener { _, _, _ ->
                    fail()
                    true
                }
                prepareAsync()
            }
        player = created
        pendingSurface?.let { runCatching { created.setDisplay(it) } }
    }

    fun fail() {
        mutableState.value = current.copy(playing = false, prepared = false, failed = true)
        dataSource?.close()
    }

    fun playWhenReady() {
        val created = player ?: return
        runCatching { created.start() }
        mutableState.value = current.copy(playing = true)
    }

    fun syncPosition() {
        val created = player ?: return
        if (!current.playing) return
        val position = runCatching { created.currentPosition }.getOrNull() ?: return
        mutableState.value = current.copy(positionMillis = position)
    }

    fun attach(surface: SurfaceHolder?) {
        pendingSurface = surface
        val created = player ?: return
        runCatching { created.setDisplay(surface) }
    }

    override fun play() {
        val created = player ?: return
        val snapshot = current
        if (snapshot.durationMillis > 0 && snapshot.positionMillis >= snapshot.durationMillis) {
            created.seekTo(0)
        }
        runCatching { created.start() }
        mutableState.value = current.copy(playing = true)
    }

    override fun pause() {
        player?.let { runCatching { it.pause() } }
        mutableState.value = current.copy(playing = false)
    }

    override fun seekTo(positionMillis: Int) {
        player?.let { runCatching { it.seekTo(positionMillis) } }
        mutableState.value = current.copy(positionMillis = positionMillis)
    }

    override fun release() {
        val created = player ?: return
        player = null
        runCatching { created.stop() }
        runCatching { created.reset() }
        runCatching { created.release() }
        dataSource?.close()
        dataSource = null
    }
}

@Composable
actual fun MediaSurface(
    controller: MediaPlaybackController,
    contentDescription: String?,
    modifier: Modifier,
) {
    val androidController = controller as? AndroidMediaPlaybackController
    AndroidView(
        factory = { context ->
            SurfaceView(context).also { surface ->
                surface.holder.addCallback(
                    object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            androidController?.attach(holder)
                        }

                        override fun surfaceChanged(
                            holder: SurfaceHolder,
                            format: Int,
                            width: Int,
                            height: Int,
                        ) = Unit

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            androidController?.attach(null)
                        }
                    },
                )
            }
        },
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest),
    )
}
