package com.m57.hermescontrol.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.m57.hermescontrol.ui.common.ComposeResourcesStringResolver

/**
 * The platform services chat needs, built for whichever target is running.
 *
 * Android assembles Room, SharedPreferences and the attachment opener from a
 * `Context`; the other targets use in-memory stores until their local
 * persistence lands (Phase 5 of the multiplatform plan). Everything below this
 * line is common, so the chat screen stops being platform-shaped.
 */
@Composable
expect fun rememberChatEnvironment(): ChatEnvironment

/** Builds the chat ViewModel from whatever environment the target supplies. */
@Composable
fun rememberChatViewModel(): ChatViewModel {
    val environment = rememberChatEnvironment()
    val resolver = remember { ComposeResourcesStringResolver() }
    return viewModel(
        factory =
            viewModelFactory {
                initializer {
                    ChatViewModel(
                        environment = environment,
                        resolver = resolver,
                        startCleanup = true,
                    )
                }
            },
    )
}

/**
 * Chat as a navigation entry.
 *
 * Replaces the Android-only wrapper that used to sit here: the screen, its
 * ViewModel and the wiring are now one common composable, so the screen graph can
 * move to `commonMain` without dragging `LocalContext` along.
 */
@Composable
fun ChatScreenEntry(
    modifier: Modifier = Modifier,
    onOpenDrawer: (() -> Unit)? = null,
    sessionId: String? = null,
) {
    ChatScreen(
        viewModel = rememberChatViewModel(),
        modifier = modifier,
        onOpenDrawer = onOpenDrawer,
        sessionId = sessionId,
    )
}
