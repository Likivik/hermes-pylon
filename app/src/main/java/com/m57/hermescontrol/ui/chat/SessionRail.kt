package com.m57.hermescontrol.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.zIndex

/**
 * Likivik patch — C7 floating rail.
 *
 * Telegram-faithful floating pill:
 *  - Frosted, rounded, floating surface (semi-transparent + elevation), no border.
 *  - ZERO margins/padding/between-items: items stack flush.
 *  - Transparent icon backgrounds: emoji (or heuristic fallback) with no tile.
 *  - Active item: 3dp Voltage Purple left-edge indicator INSIDE the rail +
 *    label tinted to the same purple.
 *  - 2-line wrapped labels.
 *  - Stale sessions collapse into an "old·N" drawer at the bottom.
 */

// ── Heuristic title→emoji for auto-assignment when no icon is set ──
private val EmojiGroups = listOf<Pair<Regex, String>>(
    Regex("[аа]dhd|ади|внимание|фокус|focus") to "🧠",
    Regex("игра|рол|rp|roleplay|tavern|silly|персонаж|сцена") to "🎲",
    Regex("hermes|агент|agent|бот|bot") to "🤖",
    Regex("диза[iяй]|design|ui|ux|макет|лого") to "🎨",
    Regex("код|code|dev|разработ|программ|билд|build|github|git|репо") to "💻",
    Regex("план|plan|roadmap|дорожная|задач|todo") to "📋",
    Regex("заметк|note|идея|idea|мозг|мысл") to "💡",
    Regex("сервер|server|хост|deploy|инфра|nix") to "🛠️",
    Regex("деньг|деньги|money|бизнес|работ|ваканс|зарплат") to "💰",
    Regex("напомин|remind|позво|провер") to "⏰",
    Regex("рус|ru |язык|перевод") to "🌐",
    Regex("купл|покуп|shopp|магаз|авито|заказ") to "🛒",
    Regex("медиц|врач|health|здоров") to "🩺",
    Regex("музык|music|песн|плейлист") to "🎵",
    Regex("фото|фото|аниме|изображ|картин|image|art|рисун") to "🖼️",
)

internal fun heuristicEmoji(title: String): String {
    val t = title.lowercase()
    for ((re, emoji) in EmojiGroups) {
        if (re.containsMatchIn(t)) return emoji
    }
    return ""
}

private val RailColors = listOf(
    Color(0xFF7C4DFF), Color(0xFF29B6F6), Color(0xFF66BB6A),
    Color(0xFFFF7043), Color(0xFFEC407A), Color(0xFF26C6DA),
    Color(0xFFAB47BC), Color(0xFFFFCA28),
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
    railMeta: Map<String, ChatViewModel.RailMeta> = emptyMap(),
    onSwitch: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    onDelete: (String) -> Unit = {},
    onEdit: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (sessions.isEmpty()) return

    val pinned = sessions.filter { it.id in pinnedSessionIds }
    val unpinned = sessions.filter { it.id !in pinnedSessionIds }
    val fresh = unpinned.filter { !it.isStale }
    val stale = unpinned.filter { it.isStale }
    val ordered = pinned + fresh + stale

    var archiveOpen by remember { mutableStateOf(false) }

    val railColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.60f)

    // Floating frosted pill: rounded, translucent, elevated, zero padding/spacing.
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = railColor,
        tonalElevation = 6.dp,
        shadowElevation = 10.dp,
        modifier = modifier
            .width(72.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(22.dp)),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Top,
        ) {
            for (session in ordered) {
                RailItem(
                    session = session,
                    meta = railMeta[session.id],
                    active = session.id == currentSessionId,
                    pinned = session.id in pinnedSessionIds,
                    onSwitch = onSwitch,
                    onTogglePin = onTogglePin,
                    onDelete = onDelete,
                    onEdit = onEdit,
                )
            }
            if (stale.isNotEmpty()) {
                ArchiveChip(count = stale.size, open = archiveOpen, onClick = { archiveOpen = !archiveOpen })
                if (archiveOpen) {
                    for (session in stale) {
                        RailItem(
                            session = session,
                            meta = railMeta[session.id],
                            active = session.id == currentSessionId,
                            pinned = false,
                            onSwitch = onSwitch,
                            onTogglePin = onTogglePin,
                            onDelete = onDelete,
                            onEdit = onEdit,
                        )
                    }
                    // when the drawer is closed the archive is NOT shown inline,
                    // so nothing to hide here beyond the open branch.
                }
            }
        }
    }
}

@Composable
private fun ArchiveChip(count: Int, open: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
    ) {
        Text(
            text = if (open) "▲" else "▾",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
        Text(
            text = "old·$count",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RailItem(
    session: SessionUi,
    meta: ChatViewModel.RailMeta?,
    active: Boolean,
    pinned: Boolean,
    onSwitch: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    onDelete: (String) -> Unit,
    onEdit: (String) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val alpha = if (session.isStale && !active) 0.38f else 1f

    // Icon: explicit emoji (meta) → heuristic auto-emoji → first letter.
    val icon = meta?.icon ?: heuristicEmoji(session.title)
    val avatarText = icon.ifEmpty { session.title.trim().take(1).uppercase() }
    val labelText = session.title.trim()

    // C7 rail cell: icon + label stack. Active styling is a 3dp purple bar OVERLAID
        // on the left edge (not a background fill) so we never eat the label.
        Box(modifier = Modifier.combinedClickable(
            onClick = { onSwitch(session.id) },
            onLongClick = { menuOpen = true },
        )) {
            // Active purple left-edge indicator — spans the cell's full height, overlaid.
            if (active) {
                Box(
                    Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(Color(0xFF7C5CFF)),
                )
            }
            // Cell content (icon + label).
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(72.dp)
                    .padding(vertical = 2.dp),
            ) {
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Edit icon / name") },
                        onClick = { menuOpen = false; onEdit(session.id) },
                    )
                    DropdownMenuItem(
                        text = { Text(if (pinned) "Unpin" else "Pin") },
                        onClick = { menuOpen = false; onTogglePin(session.id) },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = { menuOpen = false; onDelete(session.id) },
                    )
                }
                // Transparent icon: just the emoji/letter, no tile background.
                Text(
                    text = avatarText,
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (icon.isEmpty()) railColorFor(session.id).copy(alpha = alpha)
                    else Color.Unspecified,
                )
                if (pinned) {
                    Box(
                        Modifier.size(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
                Spacer(Modifier.height(1.dp))
                // 2-line wrapped label; active tints to brand purple.
                Text(
                    text = labelText,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(68.dp),
                    color = if (active) Color(0xFF7C5CFF)
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
