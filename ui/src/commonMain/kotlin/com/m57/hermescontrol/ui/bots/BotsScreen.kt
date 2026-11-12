package com.m57.hermescontrol.ui.bots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.m57.hermescontrol.NavigationController
import com.m57.hermescontrol.data.local.authStore
import com.m57.hermescontrol.data.model.ProfileInfo
import com.m57.hermescontrol.generated.resources.Res
import com.m57.hermescontrol.generated.resources.bots_empty_description
import com.m57.hermescontrol.generated.resources.bots_empty_title
import com.m57.hermescontrol.generated.resources.bots_search_placeholder
import com.m57.hermescontrol.generated.resources.bots_toggle_hidden
import com.m57.hermescontrol.generated.resources.screen_bots
import com.m57.hermescontrol.theme.Spacing
import com.m57.hermescontrol.ui.common.BotAvatar
import com.m57.hermescontrol.ui.common.EmptyState
import com.m57.hermescontrol.ui.common.ErrorState
import com.m57.hermescontrol.ui.common.HermesListItem
import com.m57.hermescontrol.ui.common.HermesScaffold
import com.m57.hermescontrol.ui.common.LoadingState
import com.m57.hermescontrol.ui.common.NavIcon
import com.m57.hermescontrol.ui.icons.HermesIcon
import com.m57.hermescontrol.ui.icons.HermesSymbol
import com.m57.hermescontrol.ui.icons.MaterialSymbol
import org.jetbrains.compose.resources.stringResource

/**
 * Route: the only part that knows about the ViewModel and the stores.
 *
 * Kept separate from [BotsScreen] so the screen can be rendered from state alone —
 * that is what makes it screenshot-testable and previewable, and what keeps the
 * "current vs unified row" comparison honest.
 */
@Composable
fun BotsRoute(
    modifier: Modifier = Modifier,
    onOpenDrawer: (() -> Unit)? = null,
    viewModel: BotsViewModel = viewModel { BotsViewModel() },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedProfileId by authStore.selectedProfileIdFlow.collectAsStateWithLifecycle()

    LaunchedEffect(selectedProfileId, state.sourceConnectionProfileId) {
        val sourceProfileId = state.sourceConnectionProfileId
        if (sourceProfileId != null && sourceProfileId != selectedProfileId && !state.isLoading) {
            viewModel.loadBots()
        }
    }

    BotsScreen(
        state = state,
        selectedProfileId = selectedProfileId,
        modifier = modifier,
        onOpenDrawer = onOpenDrawer,
        onRefresh = { viewModel.loadBots(isRefresh = true) },
        onRetry = viewModel::loadBots,
        onToggleShowHidden = viewModel::toggleShowHidden,
        onSearchQueryChange = viewModel::setSearchQuery,
        onBotClick = { profile ->
            NavigationController.openBot(profile, state.sourceConnectionProfileId)
        },
    )
}

/** Screen: a pure function of [state] and its callbacks. No ViewModel, no stores. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BotsScreen(
    state: BotsUiState,
    selectedProfileId: String?,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onToggleShowHidden: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onBotClick: (ProfileInfo) -> Unit,
    modifier: Modifier = Modifier,
    onOpenDrawer: (() -> Unit)? = null,
) {
    HermesScaffold(
        modifier = modifier,
        title = { Text(stringResource(Res.string.screen_bots)) },
        navigationIcon = onOpenDrawer?.let { NavIcon.Menu(it) },
        actions = {
            if (state.hasHiddenBots) {
                IconButton(onClick = onToggleShowHidden) {
                    MaterialSymbol(
                        if (state.showHidden) "visibility_off" else "visibility",
                        contentDescription = stringResource(Res.string.bots_toggle_hidden),
                    )
                }
            }
        },
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
    ) { paddingValues ->
        when {
            state.isLoading && state.profiles.isEmpty() -> LoadingState(Modifier.padding(paddingValues))
            state.errorMessage != null && state.profiles.isEmpty() ->
                ErrorState(state.errorMessage.orEmpty(), onRetry = onRetry)
            state.displayProfiles.isEmpty() ->
                EmptyState(
                    title = stringResource(Res.string.bots_empty_title),
                    subtitle = stringResource(Res.string.bots_empty_description),
                )
            else ->
                Column(Modifier.fillMaxSize()) {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = onSearchQueryChange,
                        leadingIcon = { HermesSymbol(icon = HermesIcon.Search, contentDescription = null) },
                        placeholder = { Text(stringResource(Res.string.bots_search_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.displayProfiles) { profile ->
                            val canOpen = canOpenBot(profile, state.sourceConnectionProfileId, selectedProfileId)
                            val onThisBotClick: (() -> Unit)? =
                                if (canOpen) {
                                    { onBotClick(profile) }
                                } else {
                                    null
                                }
                            BotCard(
                                profile = profile,
                                isActive = profile.isActiveAt(state.nowSeconds),
                                onClick = onThisBotClick,
                            )
                        }
                    }
                }
        }
    }
}

fun canOpenBot(
    profile: ProfileInfo,
    sourceConnectionProfileId: String?,
    selectedConnectionProfileId: String?,
): Boolean =
    profile.canonicalSessionId != null &&
        !sourceConnectionProfileId.isNullOrBlank() &&
        sourceConnectionProfileId == selectedConnectionProfileId

@Composable
private fun BotCard(
    profile: ProfileInfo,
    isActive: Boolean,
    onClick: (() -> Unit)?,
) {
    HermesListItem(
        leading = {
            BotAvatar(
                name = profile.name,
                avatar = profile.botMeta()?.avatar,
                size = 44.dp,
                isActive = isActive,
            )
        },
        headline = {
            Text(
                text = profile.effectiveTitle,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supporting = {
            Column {
                // Always shown, even when it repeats the title: a bot without a handle
                // reads as a different kind of row, and the handle is what the gateway
                // accepts in commands.
                Text(
                    text = "@${profile.name}",
                    style = MaterialTheme.typography.labelSmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (profile.effectiveDescription.isNotBlank()) {
                    Text(
                        text = profile.effectiveDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        onClick = onClick,
        minHeight = 88.dp,
    )
}
