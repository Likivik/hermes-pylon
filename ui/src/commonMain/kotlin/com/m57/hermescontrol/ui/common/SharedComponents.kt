package com.m57.hermescontrol.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.m57.hermescontrol.generated.resources.Res
import com.m57.hermescontrol.generated.resources.content_desc_clear_search
import com.m57.hermescontrol.theme.LocalHermesStatusColors
import com.m57.hermescontrol.theme.LocalSpacing
import com.m57.hermescontrol.ui.icons.HermesIcon
import com.m57.hermescontrol.ui.icons.HermesSymbol
import com.m57.hermescontrol.ui.icons.MaterialSymbol
import org.jetbrains.compose.resources.stringResource

// ── SectionHeader — sticky-ish section label for grouped content ────────

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val spacing = LocalSpacing.current
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md, vertical = spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
        trailing?.invoke()
    }
}

// ── StatusBadge — coloured pill with optional leading dot ──────────────

@Composable
fun StatusBadge(
    text: String,
    status: StatusBadgeType = StatusBadgeType.NEUTRAL,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val statusColors = LocalHermesStatusColors.current
    val (bgColor, fgColor) =
        when (status) {
            StatusBadgeType.SUCCESS -> {
                statusColors.successContainer to statusColors.success
            }

            StatusBadgeType.WARNING -> {
                statusColors.warningContainer to statusColors.warning
            }

            StatusBadgeType.ERROR -> {
                statusColors.errorContainer to statusColors.error
            }

            StatusBadgeType.INFO -> {
                statusColors.infoContainer to statusColors.info
            }

            StatusBadgeType.NEUTRAL -> {
                MaterialTheme.colorScheme.surfaceContainerHigh to
                    MaterialTheme.colorScheme.onSurfaceVariant
            }
        }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = bgColor,
    ) {
        Row(
            modifier =
                Modifier.padding(
                    horizontal = spacing.sm + 2.dp,
                    vertical = spacing.xs,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(6.dp)
                        .background(fgColor, RoundedCornerShape(50)),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = fgColor,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

enum class StatusBadgeType { SUCCESS, WARNING, ERROR, INFO, NEUTRAL }

// ── ToggleRow — switch row with label + description ────────────────────

@Composable
fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: String? = null,
) {
    val spacing = LocalSpacing.current
    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm + 4.dp),
            ) {
                if (icon != null) {
                    MaterialSymbol(
                        name = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    description?.let { desc ->
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        }
    }
}

// ── SearchBar — M3 search field with clear button ──────────────────────

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search…",
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier =
            modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = MaterialTheme.shapes.medium,
                ).border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    shape = MaterialTheme.shapes.medium,
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
                    if (query.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                    innerTextField()
                }
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = { onQueryChange("") },
                        modifier = Modifier.size(20.dp),
                    ) {
                        HermesSymbol(
                            icon = HermesIcon.Close,
                            contentDescription = stringResource(Res.string.content_desc_clear_search),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        },
    )
}

// ── FilterChipRow — horizontally scrollable filter chips ───────────────

@Composable
fun <T> FilterChipRow(
    chips: List<T>,
    selectedChip: T?,
    onChipSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    chipLabel: @Composable (T) -> Unit = { Text(it.toString()) },
) {
    val spacing = LocalSpacing.current
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding =
            PaddingValues(
                horizontal = spacing.md,
                vertical = spacing.xs,
            ),
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        items(chips, key = { it.toString() }) { chip ->
            FilterChip(
                selected = chip == selectedChip,
                onClick = { onChipSelected(chip) },
                label = { chipLabel(chip) },
            )
        }
    }
}

// ── StatCard — metric display card for dashboard screens ───────────────

@Composable
fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: String? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary,
) {
    val spacing = LocalSpacing.current
    Card(
        modifier = modifier,
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
    ) {
        Column(
            modifier = Modifier.padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                if (icon != null) {
                    MaterialSymbol(
                        name = icon,
                        contentDescription = null,
                        tint = accentColor,
                        size = 20.dp,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

// ── InfoRow — label/value row for detail screens ──────────────────────

@Composable
fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md, vertical = spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ── HermesListItem — the one list row ─────────────────────────────────

/**
 * The numbers and the one compound control the list rows share. A row stays a single action;
 * a cluster of controls is a value handed to its `trailing` slot, never a parameter on the row.
 */
object HermesListDefaults {
    /** Fixed row height. Fixing it also centres every slot, which Material does not do for three-line rows. */
    val RowHeight: Dp = 88.dp
    val BrandTileSize: Dp = 40.dp
    val BrandMarkSize: Dp = 22.dp
    val ClusterDividerHeight: Dp = 32.dp
    val ClusterDividerThickness: Dp = 1.5.dp

    /**
     * Switch, hairline, chevron — the arrangement used by system settings that keep "only the
     * toggle toggles" while still opening a detail page. The gap is measured to the chevron's
     * ink, not to its 48 dp touch box, so both sides of the divider look equal.
     */
    @Composable
    fun toggleTrailing(
        checked: Boolean,
        onToggle: (Boolean) -> Unit,
    ): @Composable () -> Unit =
        {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The chevron is an affordance, not a target: the row itself opens the page,
                // so a second click path here would only duplicate it.
                Icon(
                    imageVector = ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                VerticalDivider(
                    modifier =
                        Modifier
                            .padding(end = 20.dp)
                            .height(ClusterDividerHeight),
                    thickness = ClusterDividerThickness,
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                Switch(checked = checked, onCheckedChange = onToggle)
            }
        }
}

private val ChevronRight: ImageVector =
    ImageVector
        .Builder(
            name = "chevron_right",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData =
                addPathNodes(
                    "M9.29 6.71a1 1 0 0 0 0 1.41L13.17 12l-3.88 3.88a1 1 0 1 0 1.41 1.41" +
                        "l4.59-4.59a1 1 0 0 0 0-1.41L10.7 6.7a1 1 0 0 0-1.41 0z",
                ),
            fill = SolidColor(androidx.compose.ui.graphics.Color.White),
        ).build()

/**
 * One list entry. Every screen that shows a list of things should use this.
 *
 * The slots cover the three row shapes the app actually has: a destination (leading +
 * title + trailing chevron), a piece of content (leading + title + subtitle + meta +
 * trailing actions), and a control (leading + title + trailing switch).
 *
 * It wraps Material 3 [ListItem] instead of re-implementing a `Row`, for three reasons
 * that are easy to lose hand-rolling one: `ListItem` owns the container heights for
 * one/two/three-line rows, it centres `leadingContent` vertically, and it *measures*
 * the leading slot rather than constraining it — so a caller that leaves the icon size
 * alone can never have it clipped by the row.
 */

@Composable
fun HermesListItem(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    leading: @Composable (() -> Unit)? = null,
    headline: (@Composable () -> Unit)? = null,
    supporting: (@Composable () -> Unit)? = null,
    meta: @Composable (RowScope.() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    dense: Boolean = false,
    minHeight: Dp? = null,
    rowHeight: Dp? = null,
    containerColor: Color = Color.Transparent,
) {
    val spacing = LocalSpacing.current
    val metaSlot = meta
    val titleSlot = title
    val headlineSlot = headline
    val subtitleSlot = subtitle
    val supportingSlot = supporting
    // A fixed rowHeight makes every row identical, and lets the slots centre themselves:
    // Material top-aligns leading/trailing content on three-line items, which leaves a switch
    // sitting high while the text block runs lower.
    val sized =
        when {
            rowHeight != null -> modifier.height(rowHeight)
            minHeight != null -> modifier.heightIn(min = minHeight)
            else -> modifier
        }
    val centreSlot: @Composable (@Composable () -> Unit) -> Unit =
        if (rowHeight == null) {
            { slot -> slot() }
        } else {
            { slot ->
                Box(
                    modifier = Modifier.fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) { slot() }
            }
        }
    // Material's headlineContent is non-null, so the slot is resolved to one lambda here:
    // a caller-provided headline, else the title string, else nothing to show.
    // Material's headlineContent is non-null, so the slot is resolved here into a single
    // composable lambda: a caller-provided headline, else the title string, else nothing.
    // The `when` lives inside that lambda because a nullable composable slot cannot be
    // the initializer of a non-null one — Kotlin widens the branches to a common type.
    val supportingBlock: (@Composable () -> Unit)? =
        supportingSlot ?: if (subtitleSlot == null && metaSlot == null) {
            null
        } else {
            {
                Column {
                    if (subtitleSlot != null) {
                        Text(
                            text = subtitleSlot,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (metaSlot != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                            content = metaSlot,
                        )
                    }
                }
            }
        }

    val headlineOnly: @Composable () -> Unit = {
        when {
            headlineSlot != null -> headlineSlot.invoke()
            titleSlot != null ->
                Text(
                    text = titleSlot,
                    style =
                        if (dense) {
                            MaterialTheme.typography.bodyMedium
                        } else {
                            MaterialTheme.typography.bodyLarge
                        },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
        }
    }

    // With a fixed rowHeight the headline and its supporting text become one centred block.
    // Material top-aligns three-line content while centring nothing else, so leaving the slots
    // separate puts the icon, the text and the switch on three different centres.
    val headlineContent: @Composable () -> Unit =
        if (rowHeight == null) {
            headlineOnly
        } else {
            {
                Box(
                    modifier = Modifier.fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column {
                        headlineOnly()
                        supportingBlock?.invoke()
                    }
                }
            }
        }

    ListItem(
        modifier = if (onClick != null) sized.clickable(onClick = onClick) else sized,
        colors = ListItemDefaults.colors(containerColor = containerColor),
        leadingContent = leading?.let { slot -> { centreSlot(slot) } },
        headlineContent = headlineContent,
        supportingContent =
            if (rowHeight == null) {
                supportingBlock
            } else {
                null
            },
        trailingContent = trailing?.let { slot -> { centreSlot(slot) } },
    )
}
