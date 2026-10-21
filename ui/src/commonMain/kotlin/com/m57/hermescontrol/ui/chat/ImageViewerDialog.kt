package com.m57.hermescontrol.ui.chat

import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.m57.hermescontrol.AppEnvironment
import com.m57.hermescontrol.generated.resources.Res
import com.m57.hermescontrol.generated.resources.image_viewer_close
import com.m57.hermescontrol.generated.resources.image_viewer_content_desc
import com.m57.hermescontrol.generated.resources.image_viewer_load_failed
import com.m57.hermescontrol.generated.resources.image_viewer_save
import com.m57.hermescontrol.generated.resources.image_viewer_save_failed
import com.m57.hermescontrol.generated.resources.image_viewer_saved
import com.m57.hermescontrol.generated.resources.image_viewer_share
import com.m57.hermescontrol.generated.resources.image_viewer_share_failed
import com.m57.hermescontrol.theme.Spacing
import com.m57.hermescontrol.ui.icons.HermesIcon
import com.m57.hermescontrol.ui.icons.HermesSymbol
import com.m57.hermescontrol.ui.media.HermesImageBytes
import com.m57.hermescontrol.ui.media.ResolvedImage
import com.m57.hermescontrol.ui.media.rememberMediaActions
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

/**
 * Full-screen image viewer.
 *
 * Opens from any chat image (bubble thumbnail, markdown `![alt](url)`, inline
 * attachment). Provides pinch-zoom + pan, a downswipe-to-dismiss gesture, and a
 * top bar with **Save** and **Share** — both device-local, never touching the
 * Hermes server.
 *
 * Bytes are resolved once through [rememberMediaActions] and reused for
 * rendering, Save and Share: a gateway image is downloaded a single time. Save
 * and Share are platform behaviour, so they go through the same seam rather
 * than naming a `MediaStore` or an `Intent` here.
 */
@Composable
fun ImageViewerDialog(
    image: ImageViewerModel,
    onDismiss: () -> Unit,
) {
    val media = rememberMediaActions()
    val io = AppEnvironment.current.io
    val scope = rememberCoroutineScope()
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var isBusy by remember { mutableStateOf(false) }
    var resolvedImage by remember(image) { mutableStateOf<ResolvedImage?>(null) }

    val savedMsg = stringResource(Res.string.image_viewer_saved)
    val saveFailedMsg = stringResource(Res.string.image_viewer_save_failed)
    val loadFailedFmt = stringResource(Res.string.image_viewer_load_failed)
    val shareFailedMsg = stringResource(Res.string.image_viewer_share_failed)
    val contentDesc = image.name.ifBlank { stringResource(Res.string.image_viewer_content_desc) }

    LaunchedEffect(image) {
        resolvedImage =
            media.resolve(
                model = image.model,
                fallbackMime = image.mimeType,
                gatewayPath = image.gatewayPath,
            )
    }

    fun fileFor(resolved: ResolvedImage.Bytes): String = image.name.ifBlank { "hermes-image.${resolved.extension}" }

    val onSave: () -> Unit = {
        val resolved = resolvedImage as? ResolvedImage.Bytes
        if (!isBusy && resolved != null) {
            isBusy = true
            scope.launch {
                val saved =
                    withContext(io) {
                        media.saveImage(resolved.bytes, fileFor(resolved), resolved.mimeType)
                    }
                isBusy = false
                media.toast(if (saved) savedMsg else saveFailedMsg)
            }
        }
    }

    val onShare: () -> Unit = {
        val resolved = resolvedImage as? ResolvedImage.Bytes
        if (!isBusy && resolved != null) {
            isBusy = true
            scope.launch {
                val shared =
                    withContext(io) {
                        media.shareImage(resolved.bytes, fileFor(resolved), resolved.mimeType)
                    }
                isBusy = false
                if (!shared) media.toast(shareFailedMsg)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.scrim,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                when (val resolved = resolvedImage) {
                    null ->
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )

                    is ResolvedImage.Error ->
                        Text(
                            text = loadFailedFmt.replace("%s", resolved.message),
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.align(Alignment.Center).padding(Spacing.lg),
                        )

                    is ResolvedImage.Bytes ->
                        HermesImageBytes(
                            data = resolved.bytes,
                            contentDescription = contentDesc,
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .graphicsLayer(
                                        scaleX = scale,
                                        scaleY = scale,
                                        translationX = offsetX,
                                        translationY = offsetY,
                                    ).pointerInput(Unit) {
                                        detectTransformGestures(
                                            onGesture = { _, pan, zoom, _ ->
                                                val newScale = (scale * zoom).coerceIn(1f, 5f)
                                                scale = newScale
                                                offsetX = if (newScale > 1f) offsetX + pan.x else 0f
                                                offsetY += pan.y
                                                if (newScale <= 1f && offsetY > 120f) {
                                                    onDismiss()
                                                }
                                            },
                                        )
                                    },
                            contentScale = ContentScale.Fit,
                        )
                }

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(Spacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        HermesSymbol(
                            icon = HermesIcon.ArrowBack,
                            contentDescription = stringResource(Res.string.image_viewer_close),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Row {
                        if (isBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp).padding(horizontal = Spacing.sm),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        } else {
                            IconButton(
                                onClick = onSave,
                                enabled = resolvedImage is ResolvedImage.Bytes,
                            ) {
                                HermesSymbol(
                                    icon = HermesIcon.Download,
                                    contentDescription = stringResource(Res.string.image_viewer_save),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            IconButton(
                                onClick = onShare,
                                enabled = resolvedImage is ResolvedImage.Bytes,
                            ) {
                                HermesSymbol(
                                    icon = HermesIcon.Share,
                                    contentDescription = stringResource(Res.string.image_viewer_share),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
