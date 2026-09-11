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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import kotlin.math.roundToInt
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput
import com.m57.hermescontrol.theme.HermesAvatarPalette
import com.m57.hermescontrol.theme.HermesPurple

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

private val RailColors = HermesAvatarPalette
internal fun railColorFor(sessionId: String): Color {
    val hash = sessionId.fold(0) { acc, c -> (acc * 31 + c.code) and 0x7FFFFFFF }
    return RailColors[hash % RailColors.size]
}

@Composable
fun SessionRail(
    sessions: List<SessionUi>,
    currentSessionId: String?,
    pinnedSessionIds: Set<String>,
    railMeta: Map<String, RailMeta> = emptyMap(),
    order: List<String> = emptyList(),
    onReorder: (List<String>) -> Unit = {},
    modifier: Modifier = Modifier,
    onSwitch: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    onDelete: (String) -> Unit = {},
    onEdit: (String) -> Unit = {},
) {
    if (sessions.isEmpty()) return

    // Sort a group by persisted order index; unknown ids keep their relative
    // order appended after the known ones (stable).
    fun byOrder(group: List<SessionUi>): List<SessionUi> {
        val idx = order.withIndex().associate { it.value to it.index }
        return group.sortedWith(compareBy({ idx[it.id] ?: Int.MAX_VALUE }))
    }

    val pinned = byOrder(sessions.filter { it.id in pinnedSessionIds })
    val unpinned = sessions.filter { it.id !in pinnedSessionIds }
    val fresh = byOrder(unpinned.filter { !it.isStale })
    val stale = byOrder(unpinned.filter { it.isStale })
    val ordered = pinned + fresh + stale

    var archiveOpen by remember { mutableStateOf(false) }

    // Live reorder state: orderIds drives rendering (base = current ordered
    // grouping). Reorders persist via onReorder on drop. Pinned (top) and stale
    // (archive, bottom) segments are locked; only the fresh middle drags.
    var orderIds by remember(sessions, order) { mutableStateOf(ordered.map { it.id }) }
    val itemHeights = remember { mutableStateMapOf<String, Int>() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var draggingOffset by remember { mutableFloatStateOf(0f) }
    val pinnedCount = pinned.size
    val freshCount = fresh.size
    val freshEnd = pinnedCount + freshCount
    val sessionsById = remember(sessions) { sessions.associateBy { it.id } }

    fun handleDragDelta(id: String, dy: Float) {
        draggingOffset += dy
        val from = orderIds.indexOf(id)
        if (from < pinnedCount || from >= freshEnd) return
        val h = itemHeights[id] ?: return
        if (h <= 0) return
        val shift = (draggingOffset / h).roundToInt()
        if (shift == 0) return
        val to = (from + shift).coerceIn(pinnedCount, freshEnd - 1)
        if (to == from) return
        val mutable = orderIds.toMutableList()
        mutable.removeAt(from)
        mutable.add(to, id)
        orderIds = mutable
        draggingOffset -= (to - from) * h
    }

    fun handleDragEnd() {
        if (draggingId != null) onReorder(orderIds.toList())
        draggingId = null
        draggingOffset = 0f
    }

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
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 6.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Top,
        ) {
            repeat(freshEnd) { i ->
                val id = orderIds.getOrNull(i) ?: return@repeat
                val session = sessionsById[id] ?: return@repeat
                RailItem(
                    session = session,
                    meta = railMeta[id],
                    active = id == currentSessionId,
                    pinned = id in pinnedSessionIds,
                    dragging = draggingId == id,
                    dragOffset = if (draggingId == id) draggingOffset else 0f,
                    onDragStart = { draggingId = id; draggingOffset = 0f },
                    onDragDelta = { dy -> handleDragDelta(id, dy) },
                    onDragEnd = ::handleDragEnd,
                    onHeight = { itemHeights[id] = it },
                    onSwitch = onSwitch,
                    onTogglePin = onTogglePin,
                    onDelete = onDelete,
                    onEdit = onEdit,
                )
            }
            if (stale.isNotEmpty()) {
                ArchiveChip(count = stale.size, open = archiveOpen, onClick = { archiveOpen = !archiveOpen })
                if (archiveOpen) {
                    for (id in orderIds.drop(freshEnd)) {
                        val session = sessionsById[id] ?: continue
                        RailItem(
                            session = session,
                            meta = railMeta[id],
                            active = id == currentSessionId,
                            pinned = false,
                            dragging = false,
                            dragOffset = 0f,
                            onDragStart = {},
                            onDragDelta = { _ -> },
                            onDragEnd = {},
                            onHeight = { itemHeights[id] = it },
                            onSwitch = onSwitch,
                            onTogglePin = onTogglePin,
                            onDelete = onDelete,
                            onEdit = onEdit,
                        )
                    }
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
    meta: RailMeta?,
    active: Boolean,
    pinned: Boolean,
    dragging: Boolean,
    dragOffset: Float,
    onDragStart: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onHeight: (Int) -> Unit,
    onSwitch: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    onDelete: (String) -> Unit,
    onEdit: (String) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val alpha = if (session.isStale && !active) 0.38f else 1f
    // Fresh (non-pinned, non-stale) items are drag-reorderable on long-press.
    val draggable = !pinned && !session.isStale

    // Icon: explicit emoji (meta) → heuristic auto-emoji → first letter.
    val icon = meta?.icon ?: heuristicEmoji(session.title)
    val avatarText = icon.ifEmpty { session.title.trim().take(1).uppercase() }
    val labelText = session.title.trim()

    // C7 rail cell: icon + label stack. Active styling is a 3dp purple bar OVERLAID
        // on the left edge (not a background fill) so we never eat the label.
        Box(
            modifier = Modifier
                .combinedClickable(
                    onClick = { onSwitch(session.id) },
                    // Non-draggable items keep the long-press context menu; drag
                    // items open it only on a stationary long-press (no movement).
                    onLongClick = { if (!draggable) menuOpen = true },
                )
                .offset { IntOffset(0, if (dragging) dragOffset.roundToInt() else 0) }
                .zIndex(if (dragging) 1f else 0f)
                .onGloballyPositioned { onHeight(it.size.height) }
                .pointerInput(session.id) {
                    if (!draggable) return@pointerInput
                    var moved = false
                    detectDragGesturesAfterLongPress(
                        onDragStart = { onDragStart() },
                        onDrag = { change, amount ->
                            change.consume()
                            moved = true
                            onDragDelta(amount.y)
                        },
                        onDragEnd = {
                            if (moved) onDragEnd() else menuOpen = true
                        },
                        onDragCancel = { onDragEnd() },
                    )
                },
        ) {
            // Active purple left-edge indicator — spans the cell's full height, overlaid.
            if (active) {
                Box(
                    Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(HermesPurple),
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
                    color = if (active) HermesPurple
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
