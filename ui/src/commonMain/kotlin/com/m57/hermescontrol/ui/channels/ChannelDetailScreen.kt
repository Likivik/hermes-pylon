package com.m57.hermescontrol.ui.channels

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.m57.hermescontrol.data.model.MessagingPlatformUpdate
import com.m57.hermescontrol.generated.resources.Res
import com.m57.hermescontrol.generated.resources.action_cancel
import com.m57.hermescontrol.theme.Spacing
import com.m57.hermescontrol.ui.channels.components.TelegramOnboardingDialog
import com.m57.hermescontrol.ui.common.EmptyState
import com.m57.hermescontrol.ui.common.HermesListItem
import com.m57.hermescontrol.ui.common.HermesScaffold
import com.m57.hermescontrol.ui.common.NavIcon
import com.m57.hermescontrol.ui.common.ToastEffect
import com.m57.hermescontrol.ui.icons.HermesIcon
import com.m57.hermescontrol.ui.icons.HermesSymbol
import com.m57.hermescontrol.ui.icons.MaterialSymbol
import org.jetbrains.compose.resources.stringResource

/**
 * Route: loads the platform list, finds the one with [platformId] and hands it
 * to [ChannelDetailScreen]. Kept separate from the screen so the screen can be
 * rendered from state alone.
 */
@Composable
fun ChannelDetailRoute(
    platformId: String,
    onBack: () -> Unit,
    viewModel: ChannelsViewModel = viewModel { ChannelsViewModel() },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(platformId) {
        viewModel.loadPlatforms()
    }

    ToastEffect(toastMessage = state.toastMessage, onClearToast = viewModel::clearToast)

    // The QR flow lives on this page too, so its dialog is hosted here.
    if (state.onboardingPhase != OnboardingPhase.IDLE) {
        TelegramOnboardingDialog(
            state = state,
            onDismiss = viewModel::cancelTelegramOnboarding,
            onAddAllowedId = viewModel::addOnboardingAllowedId,
            onRemoveAllowedId = viewModel::removeOnboardingAllowedId,
            onNewAllowedIdChange = viewModel::setOnboardingNewAllowedId,
            onApply = viewModel::applyTelegramOnboarding,
        )
    }

    val platform = state.platforms.firstOrNull { it.id == platformId }
    if (platform == null) {
        HermesScaffold(
            title = { Text("Channel") },
            navigationIcon = NavIcon.Back(onBack),
            drawerGesturesEnabled = false,
        ) { paddingValues ->
            EmptyState(
                title = "Channel not found",
                actionLabel = "Back",
                onAction = onBack,
                modifier = Modifier.padding(paddingValues),
            )
        }
        return
    }

    ChannelDetailScreen(
        state = state,
        platformId = platformId,
        onBack = onBack,
        onTest = { viewModel.testPlatform(platformId) },
        onSave = { update -> viewModel.configurePlatform(platformId, update) },
        onRemove = { viewModel.removePlatform(platformId) },
        onStartOnboarding = viewModel::startTelegramOnboarding,
    )
}

/** Screen: rendering only. */
@Composable
fun ChannelDetailScreen(
    state: ChannelsUiState,
    platformId: String,
    onBack: () -> Unit,
    onTest: (String) -> Unit,
    onSave: (MessagingPlatformUpdate) -> Unit,
    onRemove: (String) -> Unit,
    onStartOnboarding: () -> Unit,
) {
    val platform = state.platforms.firstOrNull { it.id == platformId }
    if (platform == null) {
        HermesScaffold(
            title = { Text("Channel") },
            navigationIcon = NavIcon.Back(onBack),
            drawerGesturesEnabled = false,
        ) { paddingValues ->
            EmptyState(
                title = "Channel not found",
                actionLabel = "Back",
                onAction = onBack,
                modifier = Modifier.padding(paddingValues),
            )
        }
        return
    }

    val style = platformStateStyle(platform.state)
    var showConfigureForm by remember { mutableStateOf(false) }
    var showRemoveConfirm by remember { mutableStateOf(false) }

    HermesScaffold(
        title = { Text("Channel") },
        navigationIcon = NavIcon.Back(onBack),
        drawerGesturesEnabled = false,
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(paddingValues)
                    .padding(vertical = Spacing.sm),
        ) {
            // ── Header ──
            Row(
                modifier = Modifier.padding(horizontal = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrandTile(
                    platformId = platform.id,
                    fallbackIcon = style.icon,
                    fallbackTint = style.color,
                )
                Spacer(modifier = Modifier.width(Spacing.md))
                Column {
                    Text(
                        text = platform.name,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = style.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = style.color,
                    )
                }
            }

            val description = platform.description
            if (!description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(Spacing.sm))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.md),
                )
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            // ── Facts ──
            platform.homeChannel?.let { home ->
                DetailFactRow(
                    label = "Handle",
                    value = home.name.ifBlank { home.chatId },
                )
            }
            DetailFactRow(
                label = "Gateway",
                value = if (platform.gatewayRunning) "Running" else "Not running",
            )
            DetailFactRow(
                label = "State",
                value = style.label,
            )
            val errorMessage = platform.errorMessage
            if (!errorMessage.isNullOrBlank()) {
                DetailFactRow(
                    label = "Error",
                    value = errorMessage,
                    valueColor = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(modifier = Modifier.height(Spacing.md))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(Spacing.sm))

            // ── Actions ──
            ActionRow(
                label = "Test connection",
                onClick = { onTest(platformId) },
                busy = state.testingId == platformId,
            )

            ActionRow(
                label = if (showConfigureForm) "Close settings" else "Configure",
                onClick = { showConfigureForm = !showConfigureForm },
            )

            ActionRow(
                label = "Remove",
                onClick = { showRemoveConfirm = true },
                labelColor = MaterialTheme.colorScheme.error,
                busy = state.removingId == platformId,
            )

            if (platform.state == "not_configured" && platform.id == "telegram") {
                ActionRow(
                    label = "Set up with QR",
                    onClick = onStartOnboarding,
                    leading = {
                        HermesSymbol(
                            icon = HermesIcon.QrCode,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
            }

            if (showConfigureForm) {
                ConfigureForm(
                    platform = platform,
                    onSave = onSave,
                    onClose = { showConfigureForm = false },
                )
            }
        }
    }

    if (showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            title = { Text("Remove ${platform.name}?") },
            text = { Text("This will permanently remove this platform's configuration.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRemoveConfirm = false
                        onRemove(platformId)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirm = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }
}

/** A read-only fact: a label and its value. */
@Composable
private fun DetailFactRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    HermesListItem(
        title = label,
        supporting = {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = valueColor,
            )
        },
    )
}

/** An action row: a label that reacts to a tap, with optional leading and trailing slots. */
@Composable
private fun ActionRow(
    label: String,
    onClick: () -> Unit,
    labelColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    leading: (@Composable () -> Unit)? = null,
    busy: Boolean = false,
) {
    HermesListItem(
        headline = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = labelColor,
            )
        },
        leading = leading,
        trailing = {
            // Every action row looks reachable: a chevron, or a spinner while the action runs.
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                MaterialSymbol(
                    name = "chevron_right",
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    )
}
