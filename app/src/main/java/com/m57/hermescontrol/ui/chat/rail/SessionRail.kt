package com.m57.hermescontrol.ui.chat.rail

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.draggable2D
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.key
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
import androidx.compose.ui.zIndex
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.rememberReorderableLazyListState

/** Rail pill width — single source of truth for cells + chip + surface. */
internal val RailWidth = 72.dp
/** Item content width = pill minus the horizontal inset (4dp each side). */
internal val ItemWidth = RailWidth - 8.dp
internal val RailShape = RoundedCornerShape(22.dp)

/**
 * C7 floating rail — best-practices rewrite.
 *
 * Composition root only: receives one immutable [RailUiModel], emits [RailEvent]s.
 * LazyColumn (any list length), sh.calvin.reorderable drag (long-press, pinned/
 * stale sections non-draggable), archive drawer for stale.
 */
@Composable
fun SessionRail(
    state: RailUiModel,
    onEvent: (RailEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.sessions.isEmpty()) return

    val groups = remember(state.sessions, state.pinnedSessionIds, state.order) {
        groupRail(state.sessions, state.pinnedSessionIds, state.order)
    }
    val railColor = MaterialTheme.colorScheme.surfaceContainerHigh

    Surface(
        shape = RailShape,
        color = railColor,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
        modifier = modifier
            .width(RailWidth)
            .fillMaxHeight()
            // Single rounded clip = the ONLY containment boundary.
            .clip(RailShape),
    ) {
        RailBody(groups = groups, state = state, onEvent = onEvent)
    }
}

@Composable
private fun RailBody(
    groups: RailGroups,
    state: RailUiModel,
    onEvent: (RailEvent) -> Unit,
) {
    // LazyColumn over the FULL visible list (pinned + fresh), archive appends
    // its items after the chip when open. Drag via reorderable lib; items carry
    // stable keys so WS refreshes can't desync anything.
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val dragState = rememberReorderableLazyListState(listState) { from, to ->
        // Item keys are session ids — the library hands them straight back.
        val fromId = from.key as? String ?: return@rememberReorderableLazyListState
        val arranged = (groups.pinned.map { it.id } + groups.fresh.map { it.id } +
            if (state.archiveOpen) groups.stale.map { it.id } else emptyList())
            .toMutableList()
        val currentIndex = arranged.indexOf(fromId)
        if (currentIndex == -1) return@rememberReorderableLazyListState
        arranged.removeAt(currentIndex)
        arranged.add(to.index, fromId)
        onEvent(RailEvent.Reorder(mergeArrangement(groups, arranged)))
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize(),
            // BAND KILL TEST 1: horizontal inset REMOVED — if the shade
            // boundary tracks this padding, the band was the list viewport.
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
        userScrollEnabled = true,
    ) {
        val visible = groups.pinned + groups.fresh +
            if (state.archiveOpen) groups.stale else emptyList()
        items(items = visible, key = { it.id }) { session ->
            val display = state.displayFor(session)
            ReorderableItem(
                state = dragState,
                key = session.id,
                enabled = display.draggable,
                modifier = Modifier.fillMaxWidth(),
            ) { isDragging ->
                RailItem(
                    display = display,
                    dragging = isDragging,
                    onEvent = onEvent,
                    dragModifier = Modifier.longPressDraggableHandle(),
                )
            }
        }
        if (groups.stale.isNotEmpty()) {
            item(key = "archive_chip") {
                ArchiveChip(
                    count = groups.stale.size,
                    open = state.archiveOpen,
                    onClick = { onEvent(RailEvent.ArchiveToggle(!state.archiveOpen)) },
                )
            }
        }
    }
}

@Composable
private fun ReorderableCollectionItemScope.RailItem(
    display: RailItemDisplay,
    dragging: Boolean,
    onEvent: (RailEvent) -> Unit,
    dragModifier: Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val alpha = if (display.stale && !display.active) 0.38f else 1f
    val id = display.session.id

    Box(
        modifier = Modifier
            .width(ItemWidth)
            // Transparent item background — the frosted pill is the only fill.
            .background(Color.Transparent)
            .zIndex(if (dragging) 1f else 0f)
            .then(dragModifier)
            .combinedClickable(
                onClick = { onEvent(RailEvent.Switch(id)) },
                onLongClick = { menuOpen = true },
            ),
    ) {
        if (display.active) {
            Box(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(com.m57.hermescontrol.theme.HermesPurple),
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(ItemWidth)
                .padding(vertical = 2.dp),
        ) {
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Edit icon / name") },
                    onClick = { menuOpen = false; onEvent(RailEvent.Edit(id)) },
                )
                DropdownMenuItem(
                    text = { Text(if (display.pinned) "Unpin" else "Pin") },
                    onClick = { menuOpen = false; onEvent(RailEvent.TogglePin(id)) },
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    onClick = { menuOpen = false; onEvent(RailEvent.Delete(id)) },
                )
            }
            Text(
                text = display.icon.ifEmpty { display.label.take(1).uppercase() },
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                modifier = Modifier.width(ItemWidth),
                textAlign = TextAlign.Center,
                color = if (display.avatarIsFallbackColor) railColorFor(id).copy(alpha = alpha)
                else Color.Unspecified,
            )
            if (display.pinned) {
                Box(
                    Modifier
                        .size(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
            Spacer(Modifier.height(1.dp))
            Text(
                text = display.label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .width(ItemWidth)
                    .padding(horizontal = 3.dp),
                color = if (display.active) com.m57.hermescontrol.theme.HermesPurple
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                fontWeight = if (display.active) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun ArchiveChip(count: Int, open: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(ItemWidth)
            .combinedClickable(onClick = onClick)
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
