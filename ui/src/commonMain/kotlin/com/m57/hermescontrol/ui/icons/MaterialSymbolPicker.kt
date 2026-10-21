package com.m57.hermescontrol.ui.icons

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * Material Symbols picker — the icon counterpart to [EmojiPickerSection].
 *
 * Same contract (`selected`, `onSelect`) so a caller can host either behind a
 * tab. Backed by the variable font in [MaterialSymbols], which means the whole
 * 4,301-symbol catalog is available and searchable without shipping 4,301
 * separate vector assets.
 *
 * Selection is the symbol *name* (e.g. `"bolt"`), not a glyph, so persisted
 * values stay readable and survive a font or style change.
 */
@Composable
fun MaterialSymbolPickerSection(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var names by remember { mutableStateOf<List<String>?>(null) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { names = MaterialSymbols.entries().keys.toList() }

    Column(modifier = modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search icons…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        )
        Spacer(Modifier.height(8.dp))

        val all = names
        if (all == null) {
            Box(
                modifier = Modifier.fillMaxWidth().height(220.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@Column
        }

        val q = query.trim().lowercase().replace(' ', '_')
        val display =
            remember(all, q) {
                if (q.isEmpty()) {
                    all
                } else {
                    all.filter { it.contains(q) }
                }
            }

        if (display.isEmpty()) {
            Text(
                text = "No icons match \"$query\"",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(8),
            modifier = Modifier.height(220.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(display, key = { it }) { name ->
                SymbolTile(
                    name = name,
                    active = selected == name,
                    onClick = { onSelect(name) },
                )
            }
        }
    }
}

@Composable
private fun SymbolTile(
    name: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(
                    if (active) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                )
                .clickable(onClick = onClick)
                // A private-use glyph reads as garbage to a screen reader, so the
                // accessible name has to come from the symbol name itself.
                .semantics { contentDescription = name.replace('_', ' ') },
    ) {
        MaterialSymbol(
            name = name,
            size = 22.dp,
            tint =
                if (active) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
        )
    }
}
