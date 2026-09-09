package com.m57.hermescontrol.ui.chat

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.kodein.emoji.Emoji

/**
 * Likivik patch: full Telegram-style emoji picker for rail session icons.
 *
 * Backed by Org.Kodein.Emoji data (all groups + search). Shows the most
 * recently used icons first, then category tabs, then the grid for the active
 * tab. Selection is a plain emoji string rendered with the system emoji font.
 */
private val allGroups: List<String> by lazy { Emoji.allGroups().toList() }
private val allEmoji: List<Emoji> by lazy { allGroups.flatMap { Emoji.allOf(it) } }

@Composable
fun EmojiPickerSection(
    selected: String,
    onSelect: (String) -> Unit,
) {
    val context = LocalContext.current
    val recents = remember { readRecents(context) }
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf(allGroups.firstOrNull() ?: "") }

    Column {
        // Search
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("🔍 Search emoji…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        )
        Spacer(Modifier.height(8.dp))

        // Recents row (most recently used)
        if (recents.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                recents.forEach { emoji ->
                    EmojiTile(emoji, selected == emoji) { onSelect(it) }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // Category tabs
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(allGroups) { g ->
                val active = group == g
                Surface(
                    shape = RoundedCornerShape(50),
                    color =
                        if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable { group = g },
                ) {
                    Text(
                        text = g,
                        style = MaterialTheme.typography.labelSmall,
                        color =
                            if (active) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        // Grid for the active tab (filtered by query when non-empty).
        val display: List<Emoji> =
            if (query.isBlank()) {
                remember(group) { Emoji.allOf(group) }
            } else {
                val q = query.lowercase()
                allEmoji.filter { emoji ->
                    emoji.details.description.lowercase().contains(q) ||
                        emoji.details.aliases.any { it.lowercase().contains(q) } ||
                        emoji.details.string.contains(q)
                }
            }
        LazyVerticalGrid(
            columns = GridCells.Fixed(8),
            modifier = Modifier.height(220.dp).fillMaxWidth(),
        ) {
            items(display, key = { it.details.string }) { emoji ->
                EmojiTile(emoji.details.string, selected == emoji.details.string) { onSelect(it) }
            }
        }
    }
}

@Composable
private fun EmojiTile(emoji: String, active: Boolean, onClick: (String) -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(
                if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                else MaterialTheme.colorScheme.surfaceVariant,
            )
            .clickable { onClick(emoji) },
    ) {
        Text(emoji, style = MaterialTheme.typography.titleMedium)
    }
}

private fun recentsPrefs(context: Context) =
    context.getSharedPreferences("hermes_rail", 0)

private fun readRecents(context: Context): List<String> =
    recentsPrefs(context).getStringSet("icon_recents", emptySet())?.toList() ?: emptyList()

internal fun storeRecentIcon(context: Context, emoji: String) {
    val prefs = recentsPrefs(context)
    val cur = (prefs.getStringSet("icon_recents", emptySet()) ?: emptySet()).toMutableSet()
    cur.add(emoji)
    // Keep only the most recent 12.
    val recent = cur.takeLast(12)
    prefs.edit().putStringSet("icon_recents", recent).apply()
}
