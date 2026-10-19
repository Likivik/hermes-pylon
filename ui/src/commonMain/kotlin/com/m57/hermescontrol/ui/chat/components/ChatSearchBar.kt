package com.m57.hermescontrol.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.m57.hermescontrol.generated.resources.Res
import com.m57.hermescontrol.generated.resources.chat_clear_search_desc
import com.m57.hermescontrol.generated.resources.chat_close_search_desc
import com.m57.hermescontrol.generated.resources.chat_next_match_desc
import com.m57.hermescontrol.generated.resources.chat_prev_match_desc
import com.m57.hermescontrol.generated.resources.chat_search_placeholder
import com.m57.hermescontrol.theme.Spacing
import com.m57.hermescontrol.ui.icons.HermesIcon
import com.m57.hermescontrol.ui.icons.HermesSymbol
import org.jetbrains.compose.resources.stringResource

@Composable
fun CompactSearchInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val errorColor = MaterialTheme.colorScheme.error
    val outlineColor = if (isError) errorColor else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier =
            modifier
                .height(40.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(20.dp),
                ).border(
                    width = 1.dp,
                    color = outlineColor,
                    shape = RoundedCornerShape(20.dp),
                ).padding(horizontal = 12.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = textColor),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        decorationBox = { innerTextField ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HermesSymbol(
                    icon = HermesIcon.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.chat_search_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                    innerTextField()
                }
                if (value.isNotEmpty()) {
                    IconButton(
                        onClick = { onValueChange("") },
                        modifier = Modifier.size(20.dp),
                    ) {
                        HermesSymbol(
                            icon = HermesIcon.Close,
                            contentDescription = stringResource(Res.string.chat_clear_search_desc),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        },
    )
}

@Composable
fun SearchBarRow(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    searchMatchCount: Int,
    currentMatchIndex: Int,
    onNavigateUp: () -> Unit,
    onNavigateDown: () -> Unit,
    onClose: () -> Unit,
) {
    val isError = searchQuery.isNotEmpty() && searchMatchCount == 0

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompactSearchInput(
            value = searchQuery,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            isError = isError,
        )
        if (searchQuery.isNotEmpty()) {
            val countText =
                if (searchMatchCount > 0) {
                    "${currentMatchIndex + 1}/$searchMatchCount"
                } else {
                    "0/0"
                }
            Text(
                text = countText,
                style =
                    MaterialTheme.typography.bodySmall.copy(
                        color =
                            if (isError) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    ),
                modifier = Modifier.padding(horizontal = Spacing.xs),
            )
        }
        IconButton(
            onClick = onNavigateUp,
            enabled = searchMatchCount > 0,
            modifier = Modifier.size(36.dp),
        ) {
            HermesSymbol(
                icon = HermesIcon.KeyboardArrowUp,
                contentDescription = stringResource(Res.string.chat_prev_match_desc),
                modifier = Modifier.size(20.dp),
            )
        }
        IconButton(
            onClick = onNavigateDown,
            enabled = searchMatchCount > 0,
            modifier = Modifier.size(36.dp),
        ) {
            HermesSymbol(
                icon = HermesIcon.KeyboardArrowDown,
                contentDescription = stringResource(Res.string.chat_next_match_desc),
                modifier = Modifier.size(20.dp),
            )
        }
        IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
            HermesSymbol(
                icon = HermesIcon.Close,
                contentDescription = stringResource(Res.string.chat_close_search_desc),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
