package com.m57.hermescontrol

import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.tools.screenshot.PreviewTest
import androidx.compose.ui.tooling.preview.Preview
import com.m57.hermescontrol.theme.HermesControlTheme
import com.m57.hermescontrol.ui.chat.SessionUi
import com.m57.hermescontrol.ui.chat.RailMeta
import com.m57.hermescontrol.ui.chat.rail.RailUiModel
import com.m57.hermescontrol.ui.chat.rail.SessionRail as RailComposable

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.text.input.TextFieldValue
import com.m57.hermescontrol.data.ws.CommandCatalog
import com.m57.hermescontrol.ui.chat.ChatMessage
import com.m57.hermescontrol.ui.chat.MessageRole
import com.m57.hermescontrol.ui.chat.components.ChatMessageList
import com.m57.hermescontrol.ui.chat.components.ChatInputBar

private val FakeSessions = listOf(
    SessionUi(id = "s1", title = "ADHD support", messageCount = 4),
    SessionUi(id = "s2", title = "RP app", messageCount = 1),
    SessionUi(id = "s3", title = "Hermes", messageCount = 2),
    SessionUi(id = "s4", title = "memory", messageCount = 2),
    SessionUi(id = "s5", title = "serenity", messageCount = 0),
    SessionUi(id = "s6", title = "Plan SillyTavern alternative with research", messageCount = 0),
)

@PreviewTest
@Preview(
    name = "Full Screen: Hermes chat with C7 rail",
    device = "spec:width=440dp,height=920dp,dpi=640",
    showBackground = true,
)
@Composable
fun FullScreenC7() {
    HermesControlTheme {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(chatGradient()),
        ) {
            RailComposable(
                state = RailUiModel(
                    sessions = FakeSessions,
                    currentSessionId = "s3",
                    pinnedSessionIds = emptySet(),
                    railMeta = mapOf(
                        "s1" to RailMeta("🧠"),
                        "s2" to RailMeta("🦄"),
                        "s3" to RailMeta("💎"),
                        "s4" to RailMeta("🧠"),
                        "s5" to RailMeta("💻"),
                        "s6" to RailMeta("🚂"),
                    ),
                    order = FakeSessions.map { it.id },
                    archiveOpen = false,
                ),
                onEvent = {},
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(8.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Hermes",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    ChatMessageList(
                        messages = listOf(
                            ChatMessage(role = MessageRole.ASSISTANT, content = "👋 ready when you are"),
                            ChatMessage(role = MessageRole.USER, content = "can we make the rail floating?"),
                            ChatMessage(role = MessageRole.ASSISTANT, content = "Yep — here's the C7 design composed with the real message list."),
                        ),
                        streamingMessage = null,
                        isThinking = false,
                        thinkingText = "",
                        isSearchActive = false,
                        searchQuery = "",
                        currentSearchMatchIndex = 0,
                        searchMatchIndices = emptyList(),
                        typingEffectEnabled = true,
                        typingEffectDelayMs = 20,
                        isLoading = false,
                        isLoadingOlder = false,
                        isDark = false,
                        listState = rememberLazyListState(),
                        lastAnimatedMessageId = null,
                        onLastAnimatedMessageIdChange = {},
                        openingAttachmentPath = null,
                    )
                }
                ChatInputBar(
                    inputFieldValue = TextFieldValue(""),
                    onInputChange = {},
                    onSend = {},
                    onMicTap = {},
                    isListening = false,
                    isAgentTyping = false,
                    isConnected = true,
                    isSessionReady = true,
                    commandCatalog = CommandCatalog(),
                )
            }
        }
    }
}

private fun chatGradient(): androidx.compose.ui.graphics.Brush =
    androidx.compose.ui.graphics.Brush.verticalGradient(
        colors = listOf(
            androidx.compose.ui.graphics.Color(0xFFF6F1FF),
            androidx.compose.ui.graphics.Color(0xFFFFFFFF),
        ),
    )
