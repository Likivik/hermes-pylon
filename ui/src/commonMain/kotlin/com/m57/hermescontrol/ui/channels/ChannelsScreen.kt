package com.m57.hermescontrol.ui.channels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.m57.hermescontrol.ChannelDetailScreenKey
import com.m57.hermescontrol.NavigationController
import com.m57.hermescontrol.data.model.EnvVarField
import com.m57.hermescontrol.data.model.MessagingPlatform
import com.m57.hermescontrol.data.model.MessagingPlatformUpdate
import com.m57.hermescontrol.generated.resources.Res
import com.m57.hermescontrol.generated.resources.action_cancel
import com.m57.hermescontrol.generated.resources.channels_action_restart_now
import com.m57.hermescontrol.generated.resources.channels_action_save_settings
import com.m57.hermescontrol.generated.resources.channels_empty_desc
import com.m57.hermescontrol.generated.resources.channels_empty_title
import com.m57.hermescontrol.generated.resources.channels_restart_banner
import com.m57.hermescontrol.generated.resources.channels_sec_settings
import com.m57.hermescontrol.generated.resources.empty_action_connect_telegram
import com.m57.hermescontrol.generated.resources.screen_channels
import com.m57.hermescontrol.theme.LocalHermesStatusColors
import com.m57.hermescontrol.theme.Spacing
import com.m57.hermescontrol.ui.channels.components.TelegramOnboardingDialog
import com.m57.hermescontrol.ui.common.EmptyState
import com.m57.hermescontrol.ui.common.ErrorState
import com.m57.hermescontrol.ui.common.HermesListDefaults
import com.m57.hermescontrol.ui.common.HermesListItem
import com.m57.hermescontrol.ui.common.HermesScaffold
import com.m57.hermescontrol.ui.common.NavIcon
import com.m57.hermescontrol.ui.common.SearchBar
import com.m57.hermescontrol.ui.common.SkeletonListState
import com.m57.hermescontrol.ui.common.ToastEffect
import com.m57.hermescontrol.ui.common.listContentPadding
import com.m57.hermescontrol.ui.common.listItemSpacing
import com.m57.hermescontrol.ui.icons.HermesIcon
import com.m57.hermescontrol.ui.icons.HermesSymbol
import com.m57.hermescontrol.ui.icons.MaterialSymbol
import com.m57.hermescontrol.util.formatIsoTimestamp
import org.jetbrains.compose.resources.stringResource

// ── Validation helpers ──────────────────────────────────────────────────

private val SLACK_MEMBER_ID_RE = Regex("^[UW][A-Z0-9]{2,}$")
private val SLACK_TOKEN_PREFIXES =
    mapOf(
        "SLACK_BOT_TOKEN" to "xoxb-",
        "SLACK_APP_TOKEN" to "xapp-",
    )

private fun validateEnvField(
    field: EnvVarField,
    value: String,
): String? {
    val trimmed = value.trim()
    if (!trimmed.isNotEmpty()) return null
    val expectedPrefix = SLACK_TOKEN_PREFIXES[field.key]
    if (expectedPrefix != null && !trimmed.startsWith(expectedPrefix)) {
        return "${field.prompt ?: field.key} must start with $expectedPrefix"
    }
    if (field.key == "SLACK_ALLOWED_USERS") {
        val parts = trimmed.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val invalid = parts.firstOrNull { it != "*" && !SLACK_MEMBER_ID_RE.matches(it) }
        if (invalid != null) {
            return "$invalid does not look like a Slack member ID. Use IDs like U01ABC2DEF3."
        }
    }
    return null
}

// ── State → badge colour / label  ──────────────────────────────────────

internal enum class StatusRole { SUCCESS, WARNING, ERROR, NEUTRAL }

internal data class StateStyle(
    val label: String,
    val color: Color,
    val icon: String,
)

/** The one mapping from the backend's state strings to a label, colour and glyph. */
@Composable
internal fun platformStateStyle(state: String): StateStyle {
    val statusColors = LocalHermesStatusColors.current
    val (label, role, icon) =
        when (state) {
            "connected" -> Triple("Connected", StatusRole.SUCCESS, "check_circle")
            "pending_restart" -> Triple("Restart to apply", StatusRole.WARNING, "refresh")
            "gateway_stopped" -> Triple("Gateway stopped", StatusRole.WARNING, "radio_button_checked")
            "startup_failed" -> Triple("Start failed", StatusRole.ERROR, "warning")
            "disconnected" -> Triple("Disconnected", StatusRole.WARNING, "radio_button_checked")
            "not_configured" -> Triple("Not configured", StatusRole.NEUTRAL, "radio_button_checked")
            "disabled" -> Triple("Disabled", StatusRole.NEUTRAL, "radio_button_checked")
            "fatal" -> Triple("Error", StatusRole.ERROR, "warning")
            else -> Triple(state, StatusRole.NEUTRAL, "radio_button_checked")
        }
    val color =
        when (role) {
            StatusRole.SUCCESS -> statusColors.success
            StatusRole.WARNING -> statusColors.warning
            StatusRole.ERROR -> statusColors.error
            StatusRole.NEUTRAL -> statusColors.neutral
        }
    return StateStyle(label, color, icon)
}

// ── Main screen  ──────────────────────────────────────────────────────

/**
 * Route: the only part that knows the ViewModel. Kept separate from [ChannelsScreen] so
 * the screen can be rendered from state alone and captured as pixels.
 */
@Composable
fun ChannelsRoute(
    modifier: Modifier = Modifier,
    onOpenDrawer: (() -> Unit)? = null,
    onOpen: (String) -> Unit = { id -> NavigationController.navigateTo(ChannelDetailScreenKey(id)) },
    viewModel: ChannelsViewModel = viewModel { ChannelsViewModel() },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadPlatforms()
    }

    ToastEffect(toastMessage = state.toastMessage, onClearToast = viewModel::clearToast)

    ChannelsScreen(
        state = state,
        modifier = modifier,
        onOpenDrawer = onOpenDrawer,
        onRefresh = viewModel::loadPlatforms,
        onRetry = viewModel::loadPlatforms,
        onToggle = viewModel::togglePlatform,
        onOpen = onOpen,
        onStartOnboarding = viewModel::startTelegramOnboarding,
        onRestartGateway = viewModel::restartGateway,
        onDismissOnboarding = viewModel::cancelTelegramOnboarding,
        onAddAllowedId = viewModel::addOnboardingAllowedId,
        onRemoveAllowedId = viewModel::removeOnboardingAllowedId,
        onNewAllowedIdChange = viewModel::setOnboardingNewAllowedId,
        onApplyOnboarding = viewModel::applyTelegramOnboarding,
    )
}

/** Screen: rendering only. Search text is the one piece of local UI state it owns. */
@Composable
fun ChannelsScreen(
    state: ChannelsUiState,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onOpen: (String) -> Unit,
    onStartOnboarding: () -> Unit,
    onRestartGateway: () -> Unit,
    onDismissOnboarding: () -> Unit,
    onAddAllowedId: () -> Unit,
    onRemoveAllowedId: (String) -> Unit,
    onNewAllowedIdChange: (String) -> Unit,
    onApplyOnboarding: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenDrawer: (() -> Unit)? = null,
) {
    var query by remember { mutableStateOf("") }

    val filteredPlatforms =
        remember(query, state.platforms) {
            state.platforms.filter { platform ->
                platform.name.contains(query, ignoreCase = true)
            }
        }

    if (state.onboardingPhase != OnboardingPhase.IDLE) {
        TelegramOnboardingDialog(
            state = state,
            onDismiss = onDismissOnboarding,
            onAddAllowedId = onAddAllowedId,
            onRemoveAllowedId = onRemoveAllowedId,
            onNewAllowedIdChange = onNewAllowedIdChange,
            onApply = onApplyOnboarding,
        )
    }

    HermesScaffold(
        title = { Text(stringResource(Res.string.screen_channels)) },
        navigationIcon = onOpenDrawer?.let { NavIcon.Menu(it) },
        isRefreshing = state.isLoading,
        onRefresh = onRefresh,
    ) { paddingValues ->
        when {
            state.isLoading && state.platforms.isEmpty() -> {
                SkeletonListState(modifier = Modifier.padding(paddingValues))
            }

            state.errorMessage != null && state.platforms.isEmpty() -> {
                ErrorState(
                    message = state.errorMessage ?: "",
                    onRetry = onRetry,
                    modifier = Modifier.padding(paddingValues),
                )
            }

            state.platforms.isEmpty() -> {
                EmptyState(
                    title = stringResource(Res.string.channels_empty_title),
                    subtitle = stringResource(Res.string.channels_empty_desc),
                    actionLabel = stringResource(Res.string.empty_action_connect_telegram),
                    onAction = onStartOnboarding,
                    modifier = Modifier.padding(paddingValues),
                )
            }

            else -> {
                val gatewayRunning = state.platforms.firstOrNull()?.gatewayRunning ?: false

                val connected =
                    filteredPlatforms.filter {
                        it.state == "connected" || it.state == "pending_restart"
                    }
                val available = filteredPlatforms.filter { it !in connected }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = listContentPadding,
                    verticalArrangement = listItemSpacing,
                ) {
                    if (state.restartNeeded) {
                        item(key = "restart_banner") {
                            RestartBanner(
                                isRestarting = state.isRestarting,
                                onRestart = onRestartGateway,
                            )
                        }
                    }

                    if (!gatewayRunning && !state.restartNeeded) {
                        item(key = "gateway_offline") {
                            GatewayOfflineBanner(gatewayCommand = state.gatewayStartCommand)
                        }
                    }

                    item(key = "search") {
                        SearchBar(
                            query = query,
                            onQueryChange = { query = it },
                            placeholder = "Search channels…",
                        )
                    }

                    if (connected.isNotEmpty()) {
                        item(key = "section_connected") {
                            SectionHeaderLabel(title = "Connected")
                        }
                        items(connected, key = { it.id }) { platform ->
                            ChannelRow(
                                platform = platform,
                                onToggle = { enabled -> onToggle(platform.id, enabled) },
                                onOpen = { onOpen(platform.id) },
                            )
                        }
                    }

                    if (available.isNotEmpty()) {
                        item(key = "section_available") {
                            SectionHeaderLabel(title = "Available")
                        }
                        items(available, key = { it.id }) { platform ->
                            ChannelRow(
                                platform = platform,
                                onToggle = { enabled -> onToggle(platform.id, enabled) },
                                onOpen = { onOpen(platform.id) },
                            )
                        }
                    }

                    item(key = "admin_section") {
                        AdminSection(
                            envPath = state.envPath,
                        )
                    }
                }
            }
        }
    }
}

// ── Channel row  ───────────────────────────────────────────────────────

/**
 * One channel in the list: brand tile, name with its state, description, and the
 * switch-plus-chevron cluster. The row itself is not tappable — the chevron opens
 * the detail page, the switch toggles the channel.
 */
@Composable
private fun ChannelRow(
    platform: MessagingPlatform,
    onToggle: (Boolean) -> Unit,
    onOpen: () -> Unit,
) {
    val style = platformStateStyle(platform.state)

    HermesListItem(
        leading = {
            BrandTile(
                platformId = platform.id,
                fallbackIcon = style.icon,
                fallbackTint = style.color,
            )
        },
        headline = {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = platform.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.alignByBaseline(),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = style.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = style.color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.alignByBaseline(),
                )
            }
        },
        supporting = {
            val description = platform.description
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        trailing =
            HermesListDefaults.toggleTrailing(
                checked = platform.enabled,
                onToggle = onToggle,
            ),
        // Tapping the row opens the page; the switch and the chevron are separate targets that
        // consume their own taps, so a toggle never navigates and a chevron never toggles.
        onClick = onOpen,
        rowHeight = HermesListDefaults.RowHeight,
        containerColor = Color.Transparent,
    )
}

/** The brand tile of a channel: the mark on its brand colour, or a neutral fallback. */
@Composable
internal fun BrandTile(
    platformId: String,
    fallbackIcon: String,
    fallbackTint: Color,
    modifier: Modifier = Modifier,
) {
    val mark = brandMarkFor(platformId)
    Box(
        modifier =
            modifier
                .size(HermesListDefaults.BrandTileSize)
                .clip(RoundedCornerShape(10.dp)),
    ) {
        if (mark != null) {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .background(mark.color),
            )
            androidx.compose.foundation.Image(
                imageVector = mark.vector,
                contentDescription = null,
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .size(HermesListDefaults.BrandMarkSize),
            )
        } else {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                MaterialSymbol(
                    name = fallbackIcon,
                    contentDescription = null,
                    tint = fallbackTint,
                    size = HermesListDefaults.BrandMarkSize,
                )
            }
        }
    }
}

// ── Section header  ────────────────────────────────────────────────────

@Composable
private fun SectionHeaderLabel(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun RestartBanner(
    isRestarting: Boolean,
    onRestart: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(Res.string.channels_restart_banner),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = onRestart,
                enabled = !isRestarting,
            ) {
                if (isRestarting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = stringResource(Res.string.channels_action_restart_now),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

// ── Gateway offline banner  ───────────────────────────────────────────

@Composable
private fun GatewayOfflineBanner(gatewayCommand: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HermesSymbol(
                    icon = HermesIcon.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "The gateway is not running. Configure channels here, then start the gateway with:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = LocalHermesStatusColors.current.warningContainer,
                shape = MaterialTheme.shapes.extraSmall,
            ) {
                Text(
                    text = "  $gatewayCommand",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

// ── Configure form  ───────────────────────────────────────────────────

@Composable
fun ConfigureForm(
    platform: MessagingPlatform,
    onSave: (MessagingPlatformUpdate) -> Unit,
    onClose: () -> Unit,
) {
    val envFields = platform.envVars.orEmpty()
    val inputValues = remember { mutableStateMapOf<String, String>() }
    val fieldErrors = remember { mutableStateMapOf<String, String>() }

    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = stringResource(Res.string.channels_sec_settings),
        style = MaterialTheme.typography.titleSmall,
    )
    Spacer(modifier = Modifier.height(8.dp))

    envFields.forEach { field ->
        OutlinedTextField(
            value = inputValues[field.key].orEmpty(),
            onValueChange = {
                inputValues[field.key] = it
                fieldErrors.remove(field.key)
            },
            label = { Text(field.prompt ?: field.key) },
            placeholder = {
                if (field.isSet) {
                    Text(field.redactedValue ?: "******** (Already Configured)")
                }
            },
            supportingText = {
                fieldErrors[field.key]?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                val help = field.help
                if (help != null && !fieldErrors.containsKey(field.key)) {
                    Text(
                        text = help,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                field.expiresAt?.let { expiry ->
                    Text(
                        text = "Expires: ${formatTimestamp(expiry)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            },
            singleLine = true,
            visualTransformation =
                if (field.isPassword) {
                    PasswordVisualTransformation()
                } else {
                    androidx.compose.ui.text.input.VisualTransformation.None
                },
            modifier = Modifier.fillMaxWidth(),
            isError = fieldErrors.containsKey(field.key),
        )
        Spacer(modifier = Modifier.height(8.dp))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = onClose,
        ) {
            Text(
                text = stringResource(Res.string.action_cancel),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = {
                val errors = mutableMapOf<String, String>()
                envFields.forEach { field ->
                    val value = inputValues[field.key].orEmpty()
                    val error = validateEnvField(field, value)
                    if (error != null) errors[field.key] = error
                }
                if (errors.isNotEmpty()) {
                    fieldErrors.putAll(errors)
                    return@Button
                }
                val filledEnv = inputValues.filterValues { it.isNotBlank() }
                val update =
                    MessagingPlatformUpdate(
                        enabled = platform.enabled,
                        env = filledEnv,
                    )
                onSave(update)
                onClose()
            },
        ) {
            Text(
                text = stringResource(Res.string.channels_action_save_settings),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

// ── Admin section  ────────────────────────────────────────────────────

@Composable
private fun AdminSection(envPath: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
        ) {
            Text(
                text = "Admin",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                HermesSymbol(
                    icon = HermesIcon.Info,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Credentials are written to $envPath",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── Telegram Onboarding Dialog ────────────────────────────────────────

private fun formatTimestamp(isoDate: String): String = formatIsoTimestamp(isoDate)
