package com.m57.hermescontrol.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.m57.hermescontrol.ui.common.ComposeResourcesStringResolver

@Composable
actual fun rememberChatEnvironment(): ChatEnvironment {
    val context = LocalContext.current
    val resolver = remember { ComposeResourcesStringResolver() }
    return remember(context, resolver) { androidChatEnvironment(context, resolver) }
}
