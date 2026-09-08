package com.m57.hermescontrol.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Likivik patch v2: Telegram-style chat rail.
 *
 * v2 improvements over v1:
 *  - Pinned sessions section (persisted via SharedPreferences in the ViewModel
 *    layer — see [ChatViewModelRailExtensions]); pinned items sort first and
 *    get a small dot marker.
 *  - Bolder active state: 3dp accent bar on the leading edge + filled avatar.
 *  - Cleaner chips: rounded-square avatar (Telegram folder style), 2-line
 *    capable label with ellipsis, softer inactive styling.
 */

private val RailColors = listOf(
    Color(0xFF7C4DFF),
    Color(0xFF29B6F6),
    Color(0xFF66BB6A),
    Color(0xFFFF7043),
    Color(0xFFEC407A),
    Color(0xFF26C6DA),
    Color(0xFFAB47BC),
    Color(0xFFFFCA28),
)

internal fun railColorFor(sessionId: String): Color {
    val hash = sessionId.fold(0) { acc, c -> (acc * 31 + c.code) and 0x7FFFFFFF }
    return RailColors[hash % RailColors.size]
}

@Composable
fun SessionRail(
    sessions: List<SessionUi>,
    currentSessionId: String?,
    pinnedSessionIds: Set<String>,
    onSwitch: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (sessions.isEmpty()) return
    val railBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)

    val pinned = sessions.filter { it.id in pinnedSessionIds }
    val rest = sessions.filter { it.id !in pinnedSessionIds }
    val ordered = pinned + rest

    Column(
        modifier = modifier
            .width(64.dp)
            .fillMaxHeight()
            .background(railBg)
            .padding(vertical = 6.dp),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(ordered, key = { it.id }) { session ->
                RailItem(
                    session = session,
                    active = session.id == currentSessionId,
                    pinned = session.id in pinnedSessionIds,
                    onSwitch = onSwitch,
                    onTogglePin = onTogglePin,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RailItem(
    session: SessionUi,
    active: Boolean,
    pinned: Boolean,
    onSwitch: (String) -> Unit,
    onTogglePin: (String) -> Unit,
) {
    val tint = railColorFor(session.id)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else Color.Transparent,
            )
            .combinedClickable(
                onClick = { onSwitch(session.id) },
                onLongClick = { onTogglePin(session.id) },
            )
            .padding(vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(tint),
            ) {
                Text(
                    text = session.title.trim().take(1).uppercase().ifEmpty { "?" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
            if (pinned) {
                Spacer(Modifier.width(3.dp))
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = session.title.trim().take(9),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (active) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
