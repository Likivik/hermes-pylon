@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.m57.hermescontrol.ui.keys

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.m57.hermescontrol.data.model.EnvVarConfig
import com.m57.hermescontrol.generated.resources.Res
import com.m57.hermescontrol.generated.resources.action_cancel
import com.m57.hermescontrol.generated.resources.action_clear
import com.m57.hermescontrol.generated.resources.action_save
import com.m57.hermescontrol.generated.resources.content_desc_add_key
import com.m57.hermescontrol.generated.resources.content_desc_delete_key
import com.m57.hermescontrol.generated.resources.content_desc_edit
import com.m57.hermescontrol.generated.resources.content_desc_hide_token
import com.m57.hermescontrol.generated.resources.content_desc_refresh
import com.m57.hermescontrol.generated.resources.content_desc_show_token
import com.m57.hermescontrol.generated.resources.empty_action_clear_search
import com.m57.hermescontrol.generated.resources.keys_action_copy
import com.m57.hermescontrol.generated.resources.keys_action_delete
import com.m57.hermescontrol.generated.resources.keys_action_toggle_visibility
import com.m57.hermescontrol.generated.resources.keys_add_action
import com.m57.hermescontrol.generated.resources.keys_add_key_label
import com.m57.hermescontrol.generated.resources.keys_add_key_placeholder
import com.m57.hermescontrol.generated.resources.keys_add_value_label
import com.m57.hermescontrol.generated.resources.keys_add_value_placeholder
import com.m57.hermescontrol.generated.resources.keys_copied_toast
import com.m57.hermescontrol.generated.resources.keys_copy_warning_confirm
import com.m57.hermescontrol.generated.resources.keys_copy_warning_message
import com.m57.hermescontrol.generated.resources.keys_copy_warning_title
import com.m57.hermescontrol.generated.resources.keys_dialog_add_title
import com.m57.hermescontrol.generated.resources.keys_dialog_delete_message
import com.m57.hermescontrol.generated.resources.keys_dialog_delete_title
import com.m57.hermescontrol.generated.resources.keys_empty_desc
import com.m57.hermescontrol.generated.resources.keys_empty_title
import com.m57.hermescontrol.generated.resources.keys_filter_all
import com.m57.hermescontrol.generated.resources.keys_label_not_configured
import com.m57.hermescontrol.generated.resources.keys_no_matching_desc
import com.m57.hermescontrol.generated.resources.keys_no_matching_title
import com.m57.hermescontrol.generated.resources.keys_restart_action
import com.m57.hermescontrol.generated.resources.keys_restart_desc
import com.m57.hermescontrol.generated.resources.keys_restart_title
import com.m57.hermescontrol.generated.resources.keys_search_placeholder
import com.m57.hermescontrol.generated.resources.keys_status_configured
import com.m57.hermescontrol.generated.resources.keys_status_not_set
import com.m57.hermescontrol.generated.resources.screen_keys
import com.m57.hermescontrol.theme.LocalHermesStatusColors
import com.m57.hermescontrol.theme.Spacing
import com.m57.hermescontrol.ui.common.EmptyState
import com.m57.hermescontrol.ui.common.ErrorState
import com.m57.hermescontrol.ui.common.HermesListItem
import com.m57.hermescontrol.ui.common.HermesScaffold
import com.m57.hermescontrol.ui.common.NavIcon
import com.m57.hermescontrol.ui.common.SearchBar
import com.m57.hermescontrol.ui.common.SectionHeader
import com.m57.hermescontrol.ui.common.SkeletonListState
import com.m57.hermescontrol.ui.common.StatusBadge
import com.m57.hermescontrol.ui.common.StatusBadgeType
import com.m57.hermescontrol.ui.common.ToastEffect
import com.m57.hermescontrol.ui.icons.HermesIcon
import com.m57.hermescontrol.ui.icons.HermesSymbol
import org.jetbrains.compose.resources.stringResource

private const val FILTER_ALL = "ALL"

private val keysContentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
private val keysItemSpacing = Arrangement.spacedBy(12.dp)

// ── Route  ─────────────────────────────────────────────────────────────

/**
 * Route: the only part that knows the ViewModel. Kept separate from [KeysScreen]
 * so the screen can be rendered from state alone and captured as pixels.
 */
@Composable
fun KeysRoute(
    modifier: Modifier = Modifier,
    onOpenDrawer: (() -> Unit)? = null,
    viewModel: KeysViewModel = viewModel { KeysViewModel() },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadKeys()
    }

    ToastEffect(toastMessage = state.toastMessage, onClearToast = viewModel::clearToast)

    KeysScreen(
        state = state,
        modifier = modifier,
        onOpenDrawer = onOpenDrawer,
        onRefresh = viewModel::loadKeys,
        onRetry = viewModel::loadKeys,
        onOpenAddDialog = viewModel::openAddDialog,
        onDismissAddDialog = viewModel::dismissAddDialog,
        onNewKeyNameChange = viewModel::setNewKeyName,
        onNewKeyValueChange = viewModel::setNewKeyValue,
        onAddKey = viewModel::addKey,
        onRequestDelete = viewModel::requestDeleteKey,
        onDismissDelete = viewModel::dismissDeleteDialog,
        onConfirmDelete = viewModel::confirmDeleteKey,
        onReveal = viewModel::revealKey,
        onHide = viewModel::hideKey,
        onSaveKey = viewModel::updateKey,
        onShowToast = viewModel::showToast,
        onRestartGateway = viewModel::restartGateway,
    )
}

// ── Screen  ────────────────────────────────────────────────────────────

/**
 * Screen: a pure function of [KeysUiState] and its callbacks. No ViewModel.
 *
 * The only state it owns is local UI state: the search query and the category
 * filter. Everything else — the key list, the dialogs, the restart banner —
 * comes from [state].
 */
@Composable
fun KeysScreen(
    state: KeysUiState,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onOpenAddDialog: () -> Unit,
    onDismissAddDialog: () -> Unit,
    onNewKeyNameChange: (String) -> Unit,
    onNewKeyValueChange: (String) -> Unit,
    onAddKey: () -> Unit,
    onRequestDelete: (String) -> Unit,
    onDismissDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onReveal: (String) -> Unit,
    onHide: (String) -> Unit,
    onSaveKey: (String, String) -> Unit,
    onShowToast: (String) -> Unit,
    onRestartGateway: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenDrawer: (() -> Unit)? = null,
) {
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(FILTER_ALL) }

    val filterAllLabel = stringResource(Res.string.keys_filter_all)

    val filteredCategories =
        remember(query, state.categories) {
            if (query.isBlank()) {
                state.categories
            } else {
                state.categories.mapNotNull { section ->
                    val filtered =
                        section.vars.filter { (key, config) ->
                            key.contains(query, ignoreCase = true) ||
                                config.description?.contains(query, ignoreCase = true) == true
                        }
                    if (filtered.isNotEmpty()) {
                        section.copy(vars = filtered)
                    } else {
                        null
                    }
                }
            }
        }

    val visibleCategories =
        remember(filteredCategories, selectedCategory) {
            if (selectedCategory == FILTER_ALL) {
                filteredCategories
            } else {
                filteredCategories.filter { it.name == selectedCategory }
            }
        }

    val hasAnyVars = state.categories.any { it.vars.isNotEmpty() }

    // ── Delete Confirmation Dialog ──────────────────────────────────────
    state.deleteTargetKey?.let { key ->
        AlertDialog(
            onDismissRequest = onDismissDelete,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HermesSymbol(
                        icon = HermesIcon.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(Res.string.keys_dialog_delete_title))
                }
            },
            text = {
                Text(stringResource(Res.string.keys_dialog_delete_message, key))
            },
            confirmButton = {
                Button(
                    onClick = onConfirmDelete,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                        ),
                ) {
                    Text(stringResource(Res.string.keys_action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDelete) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }

    // ── Add Key Dialog ──────────────────────────────────────────────────
    if (state.showAddDialog) {
        AddKeyDialog(
            newKeyName = state.newKeyName,
            newKeyValue = state.newKeyValue,
            isAdding = state.isAddingKey,
            onNameChange = onNewKeyNameChange,
            onValueChange = onNewKeyValueChange,
            onAdd = onAddKey,
            onDismiss = onDismissAddDialog,
        )
    }

    HermesScaffold(
        modifier = modifier,
        title = { Text(stringResource(Res.string.screen_keys)) },
        navigationIcon = onOpenDrawer?.let { NavIcon.Menu(it) },
        isRefreshing = state.isLoading,
        onRefresh = onRefresh,
        actions = {
            IconButton(onClick = onOpenAddDialog) {
                HermesSymbol(
                    icon = HermesIcon.Add,
                    contentDescription = stringResource(Res.string.content_desc_add_key),
                )
            }
        },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading && !hasAnyVars -> {
                    SkeletonListState(modifier = Modifier.padding(paddingValues))
                }

                state.errorMessage != null && !hasAnyVars -> {
                    ErrorState(
                        message = state.errorMessage,
                        onRetry = onRetry,
                        modifier = Modifier.padding(paddingValues),
                    )
                }

                !hasAnyVars && query.isBlank() -> {
                    EmptyState(
                        title = stringResource(Res.string.keys_empty_title),
                        subtitle = stringResource(Res.string.keys_empty_desc),
                        onAction = onRefresh,
                        actionLabel = stringResource(Res.string.content_desc_refresh),
                        modifier = Modifier.padding(paddingValues),
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = keysContentPadding,
                        verticalArrangement = keysItemSpacing,
                    ) {
                        // ── 1. Restart banner (pinned at top when dirty) ─────
                        if (state.keysChanged) {
                            item(key = "restart-banner") {
                                RestartBanner(
                                    isRestarting = state.isRestartingGateway,
                                    onRestart = onRestartGateway,
                                )
                            }
                        }

                        // ── 2. Search Bar ────────────────────────────────────
                        item(key = "search") {
                            SearchBar(
                                query = query,
                                onQueryChange = { query = it },
                                placeholder = stringResource(Res.string.keys_search_placeholder),
                            )
                        }

                        // ── 3. Category Filter Chips ─────────────────────────
                        item(key = "filter-bar") {
                            CategoryFilterBar(
                                categories = state.categories,
                                selectedCategory = selectedCategory,
                                onCategorySelected = { selectedCategory = it },
                                filterAllLabel = filterAllLabel,
                            )
                        }

                        // ── 4. Keys List ─────────────────────────────────────
                        if (visibleCategories.isEmpty()) {
                            item(key = "no-results") {
                                Box(
                                    modifier = Modifier.fillParentMaxHeight(0.6f),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    EmptyState(
                                        title = stringResource(Res.string.keys_no_matching_title),
                                        subtitle = stringResource(Res.string.keys_no_matching_desc),
                                        actionLabel = stringResource(Res.string.empty_action_clear_search),
                                        onAction = {
                                            query = ""
                                            selectedCategory = FILTER_ALL
                                        },
                                    )
                                }
                            }
                        }

                        visibleCategories.forEach { section ->
                            if (selectedCategory == FILTER_ALL && filteredCategories.size > 1) {
                                item(key = "category-section-${section.name}") {
                                    SectionHeader(title = "${section.name} (${section.vars.size})")
                                }
                            }

                            items(
                                items = section.vars.toList(),
                                key = { (key, _) -> "key-$key" },
                            ) { (key, config) ->
                                EnvVarRow(
                                    key = key,
                                    config = config,
                                    categoryName = section.name,
                                    revealedValue = state.revealedValues[key],
                                    isDeleting = key in state.deletingKeys,
                                    onReveal = { onReveal(key) },
                                    onHide = { onHide(key) },
                                    onSave = { value -> onSaveKey(key, value) },
                                    onRequestDelete = { onRequestDelete(key) },
                                    onShowToast = onShowToast,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Category Filter Bar ──────────────────────────────────────────────────────

@Composable
private fun CategoryFilterBar(
    categories: List<CategorySection>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    filterAllLabel: String,
    modifier: Modifier = Modifier,
) {
    val totalCount = remember(categories) { categories.sumOf { it.vars.size } }

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
        item(key = "filter-all") {
            FilterChip(
                selected = selectedCategory == FILTER_ALL,
                onClick = { onCategorySelected(FILTER_ALL) },
                label = { Text("$filterAllLabel ($totalCount)") },
                leadingIcon = {
                    HermesSymbol(
                        icon = HermesIcon.Apps,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
        }

        items(
            items = categories,
            key = { section -> "filter-${section.name}" },
        ) { section ->
            FilterChip(
                selected = selectedCategory == section.name,
                onClick = { onCategorySelected(section.name) },
                label = { Text("${section.name} (${section.vars.size})") },
                leadingIcon = {
                    HermesSymbol(
                        icon = categoryIcon(section.name),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
        }
    }
}

private fun categoryIcon(categoryName: String): HermesIcon =
    when (categoryName) {
        "LLM Providers" -> HermesIcon.SmartToy
        "Tool API Keys" -> HermesIcon.Build
        "Messaging Platforms" -> HermesIcon.Forum
        "Agent Settings" -> HermesIcon.Tune
        else -> HermesIcon.Folder
    }

// ── Add key dialog ───────────────────────────────────────────────────────────

@Composable
private fun AddKeyDialog(
    newKeyName: String,
    newKeyValue: String,
    isAdding: Boolean,
    onNameChange: (String) -> Unit,
    onValueChange: (String) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    var valueVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isAdding) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HermesSymbol(
                    icon = HermesIcon.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(Res.string.keys_dialog_add_title))
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = newKeyName,
                    onValueChange = onNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(Res.string.keys_add_key_label)) },
                    placeholder = { Text(stringResource(Res.string.keys_add_key_placeholder)) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    trailingIcon = {
                        if (newKeyName.isNotEmpty() && !isAdding) {
                            IconButton(onClick = { onNameChange("") }) {
                                HermesSymbol(
                                    icon = HermesIcon.Clear,
                                    contentDescription = stringResource(Res.string.action_clear),
                                )
                            }
                        }
                    },
                    enabled = !isAdding,
                )

                OutlinedTextField(
                    value = newKeyValue,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(Res.string.keys_add_value_label)) },
                    placeholder = { Text(stringResource(Res.string.keys_add_value_placeholder)) },
                    visualTransformation =
                        if (valueVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                    trailingIcon = {
                        IconButton(onClick = { valueVisible = !valueVisible }) {
                            HermesSymbol(
                                icon =
                                    if (valueVisible) {
                                        HermesIcon.VisibilityOff
                                    } else {
                                        HermesIcon.Visibility
                                    },
                                contentDescription =
                                    stringResource(
                                        if (valueVisible) {
                                            Res.string.content_desc_hide_token
                                        } else {
                                            Res.string.content_desc_show_token
                                        },
                                    ),
                            )
                        }
                    },
                    enabled = !isAdding,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onAdd,
                enabled = !isAdding && newKeyName.isNotBlank() && newKeyValue.isNotBlank(),
            ) {
                if (isAdding) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(stringResource(Res.string.keys_add_action))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isAdding,
            ) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
}

@Composable
fun SensitiveCopyWarningDialog(
    secretName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(Res.string.keys_copy_warning_title))
        },
        text = {
            Text(
                stringResource(
                    Res.string.keys_copy_warning_message,
                    secretName,
                ),
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(Res.string.keys_copy_warning_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
}

// ── Env var row ───────────────────────────────────────────────────────────────

/**
 * One key, rendered on the shared [HermesListItem]: a category tile, the key name
 * as the headline, description and value as supporting content, and the row's
 * controls in the trailing slot.
 */
@Composable
private fun EnvVarRow(
    key: String,
    config: EnvVarConfig,
    categoryName: String,
    revealedValue: String?,
    isDeleting: Boolean,
    onReveal: () -> Unit,
    onHide: () -> Unit,
    onSave: (String) -> Unit,
    onRequestDelete: () -> Unit,
    onShowToast: (String) -> Unit,
) {
    var isEditing by remember { mutableStateOf(false) }
    var valueVisible by remember { mutableStateOf(false) }
    var pendingCopyValue by remember(key) { mutableStateOf<String?>(null) }
    val isRevealed = revealedValue != null
    var editedValue by remember(config, revealedValue) { mutableStateOf(revealedValue ?: "") }

    LaunchedEffect(revealedValue) {
        if (pendingCopyValue != null && pendingCopyValue != revealedValue) {
            pendingCopyValue = null
        }
    }

    val copySensitive = rememberSensitiveCopy()

    val displayValue =
        if (isRevealed) {
            revealedValue.orEmpty()
        } else if (config.isSet) {
            config.redactedValue ?: "••••••••••••"
        } else {
            stringResource(Res.string.keys_label_not_configured)
        }

    val copiedMessage = stringResource(Res.string.keys_copied_toast)

    pendingCopyValue?.let { value ->
        SensitiveCopyWarningDialog(
            secretName = key,
            onConfirm = {
                copySensitive(key, value)
                pendingCopyValue = null
                onShowToast(copiedMessage)
            },
            onDismiss = { pendingCopyValue = null },
        )
    }

    // Insert zero-width spaces () after underscores so line breaks happen cleanly between words
    val formattedKeyName = remember(key) { key.replace("_", "_\u200B") }

    HermesListItem(
        leading = {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                HermesSymbol(
                    icon = categoryIcon(categoryName),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        },
        headline = {
            Text(
                text = formattedKeyName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supporting = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusBadge(
                    text =
                        if (config.isSet) {
                            stringResource(Res.string.keys_status_configured)
                        } else {
                            stringResource(Res.string.keys_status_not_set)
                        },
                    status =
                        if (config.isSet) {
                            StatusBadgeType.SUCCESS
                        } else {
                            StatusBadgeType.WARNING
                        },
                )

                val description = config.description
                if (!description.isNullOrBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    )
                }

                if (isEditing) {
                    OutlinedTextField(
                        value = editedValue,
                        onValueChange = { editedValue = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(stringResource(Res.string.keys_add_value_label)) },
                        visualTransformation =
                            if (valueVisible) {
                                VisualTransformation.None
                            } else {
                                PasswordVisualTransformation()
                            },
                        trailingIcon = {
                            IconButton(onClick = { valueVisible = !valueVisible }) {
                                HermesSymbol(
                                    icon =
                                        if (valueVisible) {
                                            HermesIcon.VisibilityOff
                                        } else {
                                            HermesIcon.Visibility
                                        },
                                    contentDescription =
                                        stringResource(
                                            if (valueVisible) {
                                                Res.string.content_desc_hide_token
                                            } else {
                                                Res.string.content_desc_show_token
                                            },
                                        ),
                                )
                            }
                        },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        OutlinedButton(onClick = { isEditing = false }) {
                            Text(stringResource(Res.string.action_cancel))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            onSave(editedValue)
                            isEditing = false
                        }) {
                            Text(stringResource(Res.string.action_save))
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = Spacing.sm)
                                    .horizontalScroll(rememberScrollState()),
                        ) {
                            Text(
                                text = displayValue,
                                style = MaterialTheme.typography.bodyMedium,
                                color =
                                    if (config.isSet) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    },
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }
        },
        trailing = {
            if (!isEditing) {
                // Anchored right: with the reveal and copy buttons conditional, the cluster must
                // grow leftwards so edit and delete keep the same column on every row.
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (config.isSet) {
                        IconButton(
                            onClick = if (isRevealed) onHide else onReveal,
                            modifier = Modifier.size(40.dp),
                        ) {
                            HermesSymbol(
                                icon =
                                    if (isRevealed) {
                                        HermesIcon.VisibilityOff
                                    } else {
                                        HermesIcon.Visibility
                                    },
                                contentDescription = stringResource(Res.string.keys_action_toggle_visibility),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (isRevealed && !revealedValue.isNullOrEmpty()) {
                            IconButton(
                                onClick = { pendingCopyValue = revealedValue },
                                modifier = Modifier.size(40.dp),
                            ) {
                                HermesSymbol(
                                    icon = HermesIcon.ContentCopy,
                                    contentDescription = stringResource(Res.string.keys_action_copy),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    IconButton(
                        modifier = Modifier.size(40.dp),
                        onClick = {
                            editedValue = if (isRevealed) revealedValue.orEmpty() else ""
                            isEditing = true
                        },
                    ) {
                        HermesSymbol(
                            icon = HermesIcon.Edit,
                            contentDescription = stringResource(Res.string.content_desc_edit),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(
                        onClick = onRequestDelete,
                        enabled = !isDeleting,
                        modifier = Modifier.size(40.dp),
                    ) {
                        if (isDeleting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.error,
                            )
                        } else {
                            HermesSymbol(
                                icon = HermesIcon.Delete,
                                contentDescription = stringResource(Res.string.content_desc_delete_key),
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            )
                        }
                    }
                }
            }
        },
        minHeight = 88.dp,
    )
}

// ── Restart banner ────────────────────────────────────────────────────────────

@Composable
private fun RestartBanner(
    isRestarting: Boolean,
    onRestart: () -> Unit,
) {
    val statusColors = LocalHermesStatusColors.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, statusColors.warning.copy(alpha = 0.4f)),
        colors =
            CardDefaults.cardColors(
                containerColor = statusColors.warningContainer,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.keys_restart_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColors.warning,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(Res.string.keys_restart_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColors.warning.copy(alpha = 0.9f),
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Button(
                onClick = onRestart,
                enabled = !isRestarting,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = statusColors.warning,
                        contentColor = MaterialTheme.colorScheme.surface,
                    ),
            ) {
                if (isRestarting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.surface,
                    )
                } else {
                    HermesSymbol(
                        icon = HermesIcon.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(Res.string.keys_restart_action))
                }
            }
        }
    }
}
