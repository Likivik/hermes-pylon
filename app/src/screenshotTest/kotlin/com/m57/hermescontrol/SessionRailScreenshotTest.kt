package com.m57.hermescontrol

import androidx.compose.ui.unit.dp
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
import com.m57.hermescontrol.ui.chat.SessionRail
import com.m57.hermescontrol.ui.chat.SessionUi
import com.m57.hermescontrol.ui.chat.ChatViewModel

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text

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
    device = "spec:width=440dp,height=920dp,dpi=640", // Pixel-ish phone @ high dpi
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
            // Rail mock on the leading edge (real SessionRail composable).
            SessionRail(
                sessions = FakeSessions,
                currentSessionId = "s3",
                pinnedSessionIds = emptySet(),
                railMeta = mapOf(
                    "s1" to ChatViewModel.RailMeta("🧠"),
                    "s2" to ChatViewModel.RailMeta("🦄"),
                    "s3" to ChatViewModel.RailMeta("💎"),
                    "s4" to ChatViewModel.RailMeta("🧠"),
                    "s5" to ChatViewModel.RailMeta("💻"),
                    "s6" to ChatViewModel.RailMeta("🚂"),
                ),
                onSwitch = {},
                onTogglePin = {},
                onDelete = {},
                onEdit = {},
            )
            // Mock chat pane background gradient (mirrors ChatScreen's backgroundGradient).
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(8.dp),
            ) {
                // Mock title bar
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
                // Mock chat bubble cards
                listOf(
                    "👋 ready when you are",
                    "(the rest of the chat pane is mocked for the screenshot test)",
                ).forEach { msg ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    ) {
                        Text(
                            text = msg,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// Cheap decorative gradient used by the rail preview to mimic the chat gradient.
private fun chatGradient(): androidx.compose.ui.graphics.Brush =
    androidx.compose.ui.graphics.Brush.verticalGradient(
        colors = listOf(
            androidx.compose.ui.graphics.Color(0xFFF6F1FF),
            androidx.compose.ui.graphics.Color(0xFFFFFFFF),
        ),
    )
