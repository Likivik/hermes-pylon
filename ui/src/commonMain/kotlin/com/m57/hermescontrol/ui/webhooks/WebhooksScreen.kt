package com.m57.hermescontrol.ui.webhooks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.m57.hermescontrol.data.model.WebhookSubscription
import com.m57.hermescontrol.generated.resources.Res
import com.m57.hermescontrol.generated.resources.action_cancel
import com.m57.hermescontrol.generated.resources.screen_webhooks
import com.m57.hermescontrol.generated.resources.webhooks_action_add
import com.m57.hermescontrol.generated.resources.webhooks_action_create
import com.m57.hermescontrol.generated.resources.webhooks_action_delete
import com.m57.hermescontrol.generated.resources.webhooks_dialog_create_title
import com.m57.hermescontrol.generated.resources.webhooks_dialog_delete_message
import com.m57.hermescontrol.generated.resources.webhooks_dialog_delete_title
import com.m57.hermescontrol.generated.resources.webhooks_empty_desc
import com.m57.hermescontrol.generated.resources.webhooks_empty_title
import com.m57.hermescontrol.generated.resources.webhooks_field_deliver
import com.m57.hermescontrol.generated.resources.webhooks_field_description
import com.m57.hermescontrol.generated.resources.webhooks_field_events
import com.m57.hermescontrol.generated.resources.webhooks_field_name
import com.m57.hermescontrol.generated.resources.webhooks_field_secret
import com.m57.hermescontrol.generated.resources.webhooks_hint_deliver
import com.m57.hermescontrol.generated.resources.webhooks_hint_events
import com.m57.hermescontrol.generated.resources.webhooks_hint_name
import com.m57.hermescontrol.generated.resources.webhooks_hint_secret
import com.m57.hermescontrol.generated.resources.webhooks_no_matching
import com.m57.hermescontrol.generated.resources.webhooks_no_matching_desc
import com.m57.hermescontrol.generated.resources.webhooks_search_placeholder
import com.m57.hermescontrol.generated.resources.webhooks_sec_status
import com.m57.hermescontrol.generated.resources.webhooks_sec_subscriptions
import com.m57.hermescontrol.theme.Spacing
import com.m57.hermescontrol.ui.common.DetailDialog
import com.m57.hermescontrol.ui.common.EmptyState
import com.m57.hermescontrol.ui.common.ErrorState
import com.m57.hermescontrol.ui.common.HermesListItem
import com.m57.hermescontrol.ui.common.HermesScaffold
import com.m57.hermescontrol.ui.common.NavIcon
import com.m57.hermescontrol.ui.common.SearchBar
import com.m57.hermescontrol.ui.common.SkeletonListState
import com.m57.hermescontrol.ui.common.StatusBadge
import com.m57.hermescontrol.ui.common.StatusBadgeType
import com.m57.hermescontrol.ui.common.ToastEffect
import com.m57.hermescontrol.ui.common.listContentPadding
import com.m57.hermescontrol.ui.common.listItemSpacing
import com.m57.hermescontrol.ui.common.toDetailRows
import com.m57.hermescontrol.ui.icons.HermesIcon
import com.m57.hermescontrol.ui.icons.HermesSymbol
import org.jetbrains.compose.resources.stringResource

/**
 * Route: the only part that knows the ViewModel. Kept separate from [WebhooksScreen]
 * so the screen can be rendered from state alone and captured as pixels.
 */
@Composable
fun WebhooksRoute(
    modifier: Modifier = Modifier,
    onOpenDrawer: (() -> Unit)? = null,
    viewModel: WebhooksViewModel = viewModel { WebhooksViewModel() },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadWebhooks()
    }

    ToastEffect(toastMessage = state.toastMessage, onClearToast = viewModel::clearToast)

    WebhooksScreen(
        state = state,
        modifier = modifier,
        onOpenDrawer = onOpenDrawer,
        onRefresh = { viewModel.loadWebhooks() },
        onRetry = { viewModel.loadWebhooks() },
        onToggleGlobal = { enabled -> viewModel.toggleWebhooks(enabled) },
        onToggleSubscription = { name, enabled -> viewModel.toggleSubscription(name, enabled) },
        onShowCreateDialog = viewModel::showCreateDialog,
        onDismissCreateDialog = viewModel::dismissCreateDialog,
        onCreateFormNameChange = viewModel::updateCreateFormName,
        onCreateFormDescriptionChange = viewModel::updateCreateFormDescription,
        onCreateFormEventsChange = viewModel::updateCreateFormEvents,
        onCreateFormDeliverChange = viewModel::updateCreateFormDeliver,
        onCreateFormSecretChange = viewModel::updateCreateFormSecret,
        onCreateSubscription = viewModel::createSubscription,
        onPromptDeleteSubscription = viewModel::promptDeleteSubscription,
        onDismissDeleteDialog = viewModel::dismissDeleteDialog,
        onConfirmDeleteSubscription = viewModel::confirmDeleteSubscription,
    )
}

/**
 * Screen: rendering only. The search query and the detail-dialog selection are the
 * only pieces of local UI state it owns.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebhooksScreen(
    state: WebhooksUiState,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onToggleGlobal: (Boolean) -> Unit,
    onToggleSubscription: (String, Boolean) -> Unit,
    onShowCreateDialog: () -> Unit,
    onDismissCreateDialog: () -> Unit,
    onCreateFormNameChange: (String) -> Unit,
    onCreateFormDescriptionChange: (String) -> Unit,
    onCreateFormEventsChange: (String) -> Unit,
    onCreateFormDeliverChange: (String) -> Unit,
    onCreateFormSecretChange: (String) -> Unit,
    onCreateSubscription: () -> Unit,
    onPromptDeleteSubscription: (WebhookSubscription) -> Unit,
    onDismissDeleteDialog: () -> Unit,
    onConfirmDeleteSubscription: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenDrawer: (() -> Unit)? = null,
) {
    var query by remember { mutableStateOf("") }
    var showDetail by remember { mutableStateOf<WebhookSubscription?>(null) }

    val filteredSubscriptions =
        remember(query, state.subscriptions) {
            state.subscriptions.filter { sub ->
                sub.name.contains(query, ignoreCase = true) ||
                    sub.description?.contains(query, ignoreCase = true) == true ||
                    (sub.events?.any { it.contains(query, ignoreCase = true) } == true)
            }
        }

    // ── Delete Confirmation Dialog ──
    state.deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = onDismissDeleteDialog,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HermesSymbol(
                        icon = HermesIcon.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        size = 20.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(Res.string.webhooks_dialog_delete_title))
                }
            },
            text = {
                Text(stringResource(Res.string.webhooks_dialog_delete_message, target.name))
            },
            confirmButton = {
                Button(
                    onClick = onConfirmDeleteSubscription,
                    enabled = !state.isDeleting,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                        ),
                ) {
                    if (state.isDeleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onError,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(stringResource(Res.string.webhooks_action_delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissDeleteDialog,
                    enabled = !state.isDeleting,
                ) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }

    // ── Create Subscription Dialog ──
    if (state.showCreateDialog) {
        AlertDialog(
            onDismissRequest = onDismissCreateDialog,
            title = { Text(stringResource(Res.string.webhooks_dialog_create_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = state.createFormName,
                        onValueChange = onCreateFormNameChange,
                        label = { Text(stringResource(Res.string.webhooks_field_name)) },
                        placeholder = { Text(stringResource(Res.string.webhooks_hint_name)) },
                        singleLine = true,
                        isError = state.createFormError != null,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = state.createFormDescription,
                        onValueChange = onCreateFormDescriptionChange,
                        label = { Text(stringResource(Res.string.webhooks_field_description)) },
                        placeholder = { Text("What this webhook does...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = state.createFormEvents,
                        onValueChange = onCreateFormEventsChange,
                        label = { Text(stringResource(Res.string.webhooks_field_events)) },
                        placeholder = { Text(stringResource(Res.string.webhooks_hint_events)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = state.createFormDeliver,
                        onValueChange = onCreateFormDeliverChange,
                        label = { Text(stringResource(Res.string.webhooks_field_deliver)) },
                        placeholder = { Text(stringResource(Res.string.webhooks_hint_deliver)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    OutlinedTextField(
                        value = state.createFormSecret,
                        onValueChange = onCreateFormSecretChange,
                        label = { Text(stringResource(Res.string.webhooks_field_secret)) },
                        placeholder = { Text(stringResource(Res.string.webhooks_hint_secret)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    state.createFormError?.let { error ->
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = onCreateSubscription,
                    enabled = !state.isActionRunning,
                ) {
                    if (state.isActionRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(stringResource(Res.string.webhooks_action_create))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissCreateDialog,
                    enabled = !state.isActionRunning,
                ) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }

    HermesScaffold(
        modifier = modifier,
        title = { Text(stringResource(Res.string.screen_webhooks)) },
        navigationIcon = onOpenDrawer?.let { NavIcon.Menu(it) },
        isRefreshing = state.isLoading,
        onRefresh = onRefresh,
        actions = {
            IconButton(onClick = onShowCreateDialog) {
                HermesSymbol(
                    icon = HermesIcon.Add,
                    contentDescription = stringResource(Res.string.webhooks_action_add),
                )
            }
        },
    ) { paddingValues ->
        when {
            state.isLoading && state.baseUrl == null -> {
                SkeletonListState(modifier = Modifier.padding(paddingValues))
            }

            state.errorMessage != null && state.baseUrl == null -> {
                ErrorState(
                    message = state.errorMessage ?: "",
                    onRetry = onRetry,
                    modifier = Modifier.padding(paddingValues),
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = listContentPadding,
                    verticalArrangement = listItemSpacing,
                ) {
                    // Global Status Switch
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors =
                                CardDefaults.cardColors(
                                    containerColor =
                                        if (state.enabled) {
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        },
                                ),
                        ) {
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(Spacing.md),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(Res.string.webhooks_sec_status),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    state.baseUrl?.let {
                                        Text(
                                            text = "Receiver URL: $it",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Switch(
                                    checked = state.enabled,
                                    onCheckedChange = onToggleGlobal,
                                )
                            }
                        }
                    }

                    // Subscriptions header
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(Res.string.webhooks_sec_subscriptions),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "(${state.subscriptions.size})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    item {
                        SearchBar(
                            query = query,
                            onQueryChange = { query = it },
                            placeholder = stringResource(Res.string.webhooks_search_placeholder),
                        )
                    }

                    if (filteredSubscriptions.isEmpty()) {
                        item {
                            EmptyState(
                                title =
                                    if (state.subscriptions.isEmpty()) {
                                        stringResource(Res.string.webhooks_empty_title)
                                    } else {
                                        stringResource(Res.string.webhooks_no_matching)
                                    },
                                subtitle =
                                    if (state.subscriptions.isEmpty()) {
                                        stringResource(Res.string.webhooks_empty_desc)
                                    } else {
                                        stringResource(Res.string.webhooks_no_matching_desc)
                                    },
                                actionLabel =
                                    if (state.subscriptions.isEmpty()) {
                                        stringResource(Res.string.webhooks_action_add)
                                    } else {
                                        null
                                    },
                                onAction =
                                    if (state.subscriptions.isEmpty()) {
                                        onShowCreateDialog
                                    } else {
                                        null
                                    },
                            )
                        }
                    } else {
                        items(filteredSubscriptions, key = { it.name }) { sub ->
                            SubscriptionRow(
                                sub = sub,
                                isToggling = state.togglingName == sub.name,
                                onToggle = { enabled -> onToggleSubscription(sub.name, enabled) },
                                onDelete = { onPromptDeleteSubscription(sub) },
                                onClick = { showDetail = sub },
                            )
                        }
                    }
                }
            }
        }
    }

    showDetail?.let { sub ->
        DetailDialog(
            title = sub.name,
            rows = sub.toDetailRows(),
            onDismiss = { showDetail = null },
        )
    }
}

/**
 * One subscription, rendered on the shared [HermesListItem] so the row matches
 * every other list in the app.
 */
@Composable
private fun SubscriptionRow(
    sub: WebhookSubscription,
    isToggling: Boolean,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
) {
    val isEnabled = sub.enabled ?: true

    HermesListItem(
        modifier = Modifier.fillMaxWidth(),
        leading = {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color =
                    if (isEnabled) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                modifier = Modifier.size(40.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HermesSymbol(
                        icon = HermesIcon.Webhook,
                        contentDescription = null,
                        size = 22.dp,
                        tint =
                            if (isEnabled) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }
            }
        },
        headline = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = sub.name.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color =
                        if (isEnabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (sub.secret_set == true) {
                    Spacer(modifier = Modifier.width(6.dp))
                    HermesSymbol(
                        icon = HermesIcon.Lock,
                        contentDescription = "Secret configured",
                        size = 14.dp,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                // Delivery target badge
                sub.deliver?.let { deliver ->
                    if (deliver.isNotBlank() && deliver != "log") {
                        Spacer(modifier = Modifier.width(6.dp))
                        StatusBadge(
                            text = deliver,
                            status = StatusBadgeType.INFO,
                        )
                    }
                }
            }
        },
        supporting = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                sub.description?.let { desc ->
                    if (desc.isNotBlank()) {
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                sub.events?.let { events ->
                    if (events.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            events.forEach { event ->
                                SuggestionChip(
                                    onClick = {},
                                    label = {
                                        Text(
                                            text = event,
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }

                Text(
                    text = sub.url,
                    style =
                        MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        trailing = {
            // Anchored right: the spinner and the toggle are conditional, so the cluster must
            // grow leftwards and keep delete in the same column on every row.
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isToggling) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = onToggle,
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp),
                ) {
                    HermesSymbol(
                        icon = HermesIcon.Delete,
                        contentDescription = "Delete subscription",
                        size = 18.dp,
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    )
                }
            }
        },
        onClick = onClick,
    )
}
