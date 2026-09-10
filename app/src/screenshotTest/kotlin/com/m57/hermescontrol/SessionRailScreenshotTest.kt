package com.m57.hermescontrol

import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.tools.screenshot.PreviewTest
import androidx.compose.ui.tooling.preview.Preview
import com.m57.hermescontrol.theme.HermesControlTheme
import com.m57.hermescontrol.ui.chat.SessionRail
import com.m57.hermescontrol.ui.chat.SessionUi
import com.m57.hermescontrol.ui.chat.ChatViewModel

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
    name = "SessionRail C7",
    device = "spec:width=120dp,height=700dp,dpi=640", // high dpi: 3.dp indicator ~= 19px (readable)
)
@Composable
fun SessionRailC7() {
    HermesControlTheme {
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
            modifier = Modifier.padding(8.dp),
        )
    }
}
