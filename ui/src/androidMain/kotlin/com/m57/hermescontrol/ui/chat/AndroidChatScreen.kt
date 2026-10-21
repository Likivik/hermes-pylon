package com.m57.hermescontrol.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.m57.hermescontrol.ui.common.ComposeResourcesStringResolver

/**
 * Android entry point for the chat screen.
 *
 * It exists only to assemble the platform services [ChatViewModel] needs; the
 * screen itself is in commonMain and takes an already-built ViewModel.
 */
@Composable
fun AndroidChatScreen(
    modifier: Modifier = Modifier,
    onOpenDrawer: (() -> Unit)? = null,
    sessionId: String? = null,
) {
    val context = LocalContext.current
    val resolver = remember { ComposeResourcesStringResolver() }
    val chatViewModel: ChatViewModel =
        viewModel(
            factory =
                viewModelFactory {
                    initializer {
                        ChatViewModel(
                            environment = androidChatEnvironment(context, resolver),
                            resolver = resolver,
                            startCleanup = true,
                        )
                    }
                },
        )
    ChatScreen(
        viewModel = chatViewModel,
        modifier = modifier,
        onOpenDrawer = onOpenDrawer,
        sessionId = sessionId,
    )
}
