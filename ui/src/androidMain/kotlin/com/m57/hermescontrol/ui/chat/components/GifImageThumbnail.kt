package com.m57.hermescontrol.ui.chat.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.m57.hermescontrol.data.remote.GatewayFileClient
import com.m57.hermescontrol.data.remote.GatewayFileResult
import com.m57.hermescontrol.generated.resources.Res
import com.m57.hermescontrol.generated.resources.gif_action_pause
import com.m57.hermescontrol.generated.resources.gif_action_play
import org.jetbrains.compose.resources.stringResource

@Composable
fun GifImageThumbnail(
    model: Any,
    gatewayPath: String? = null,
    contentDescription: String?,
    isGif: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    gatewayFetcher: suspend (String, java.io.File) -> GatewayFileResult = GatewayFileClient::fetch,
) {
    val context = LocalContext.current
    val playDescription = stringResource(Res.string.gif_action_play)
    val pauseDescription = stringResource(Res.string.gif_action_pause)
    var resolvedModel by remember(model, gatewayPath) {
        mutableStateOf<Any?>(if (gatewayPath == null) model else null)
    }
    var isGatewayLoading by remember(model, gatewayPath) {
        mutableStateOf(gatewayPath != null)
    }

    LaunchedEffect(model, gatewayPath) {
        if (gatewayPath == null) {
            resolvedModel = model
            isGatewayLoading = false
            return@LaunchedEffect
        }
        resolvedModel =
            when (val result = gatewayFetcher(gatewayPath, java.io.File(context.cacheDir, "gateway_media"))) {
                is GatewayFileResult.Success -> result.file.cacheFile
                else -> null
            }
        isGatewayLoading = false
    }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .clickable { onClick() },
    ) {
        AsyncImage(
            model = resolvedModel,
            contentDescription = contentDescription,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium),
            contentScale = ContentScale.FillWidth,
        )

        if (isGatewayLoading) {
            CircularProgressIndicator(
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .size(28.dp)
                        .testTag("gateway-media-loading"),
            )
        }
    }
}
