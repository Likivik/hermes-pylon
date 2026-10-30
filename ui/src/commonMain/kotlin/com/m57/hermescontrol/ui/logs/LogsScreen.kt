package com.m57.hermescontrol.ui.logs

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.m57.hermescontrol.generated.resources.Res
import com.m57.hermescontrol.generated.resources.error_unknown
import com.m57.hermescontrol.generated.resources.logs_action_export
import com.m57.hermescontrol.generated.resources.logs_action_pause
import com.m57.hermescontrol.generated.resources.logs_action_resume
import com.m57.hermescontrol.generated.resources.logs_action_upload
import com.m57.hermescontrol.generated.resources.logs_content_desc_jump_bottom
import com.m57.hermescontrol.generated.resources.logs_empty_desc
import com.m57.hermescontrol.generated.resources.logs_empty_title
import com.m57.hermescontrol.generated.resources.logs_filter_agent
import com.m57.hermescontrol.generated.resources.logs_filter_all
import com.m57.hermescontrol.generated.resources.logs_filter_cli
import com.m57.hermescontrol.generated.resources.logs_filter_component
import com.m57.hermescontrol.generated.resources.logs_filter_cron
import com.m57.hermescontrol.generated.resources.logs_filter_debug
import com.m57.hermescontrol.generated.resources.logs_filter_error
import com.m57.hermescontrol.generated.resources.logs_filter_errors
import com.m57.hermescontrol.generated.resources.logs_filter_file
import com.m57.hermescontrol.generated.resources.logs_filter_gateway
import com.m57.hermescontrol.generated.resources.logs_filter_info
import com.m57.hermescontrol.generated.resources.logs_filter_level
import com.m57.hermescontrol.generated.resources.logs_filter_lines
import com.m57.hermescontrol.generated.resources.logs_filter_tools
import com.m57.hermescontrol.generated.resources.logs_filter_warning
import com.m57.hermescontrol.generated.resources.screen_logs
import com.m57.hermescontrol.theme.LocalSpacing
import com.m57.hermescontrol.ui.common.EmptyState
import com.m57.hermescontrol.ui.common.ErrorState
import com.m57.hermescontrol.ui.common.HermesScaffold
import com.m57.hermescontrol.ui.common.NavIcon
import com.m57.hermescontrol.ui.common.SearchBar
import com.m57.hermescontrol.ui.common.SkeletonListState
import com.m57.hermescontrol.ui.common.ToastEffect
import com.m57.hermescontrol.ui.icons.HermesIcon
import com.m57.hermescontrol.ui.icons.HermesSymbol
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

private data class LogsFilterOption(
    val value: String,
    val label: String,
)

@Composable
fun LogsScreen(
    modifier: Modifier = Modifier,
    onOpenDrawer: (() -> Unit)? = null,
    viewModel: LogsViewModel = viewModel { LogsViewModel() },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = LocalSpacing.current
    val listState = rememberLazyListState()

    var query by remember { mutableStateOf("") }
    var pauseScroll by remember { mutableStateOf(false) }
    val filters = state.filters

    // Client-side free-text filter over the server-returned lines
    val filteredLogs =
        remember(query, state.logs) {
            if (query.isBlank()) {
                state.logs
            } else {
                state.logs.filter { it.contains(query, ignoreCase = true) }
            }
        }

    LaunchedEffect(Unit) {
        viewModel.loadLogs()
    }

    // Auto-refresh logs via polling while the screen is visible
    DisposableEffect(Unit) {
        viewModel.startAutoRefresh()
        onDispose { viewModel.stopAutoRefresh() }
    }

    ToastEffect(toastMessage = state.toastMessage, onClearToast = viewModel::clearToast)

    // Auto-scroll to bottom when new logs arrive (unless paused)
    LaunchedEffect(filteredLogs.size, pauseScroll) {
        if (!pauseScroll && filteredLogs.isNotEmpty()) {
            listState.scrollToItem(Int.MAX_VALUE)
        }
    }

    // Build the export text from filtered logs
    val exportText =
        remember(filteredLogs) {
            filteredLogs.joinToString("\n")
        }
    val shareLogs = rememberLogSharer()
    val scope = rememberCoroutineScope()

    HermesScaffold(
        title = { Text(stringResource(Res.string.screen_logs)) },
        navigationIcon = onOpenDrawer?.let { NavIcon.Menu(it) },
        isRefreshing = state.isLoading,
        onRefresh = { viewModel.loadLogs() },
        actions = {
            // Pause / Resume toggle
            IconButton(onClick = { pauseScroll = !pauseScroll }) {
                HermesSymbol(
                    icon = if (pauseScroll) HermesIcon.PlayArrow else HermesIcon.Pause,
                    contentDescription =
                        stringResource(
                            if (pauseScroll) Res.string.logs_action_resume else Res.string.logs_action_pause,
                        ),
                )
            }
            // Share / Export
            IconButton(
                onClick = {
                    shareLogs(exportText)
                },
                enabled = filteredLogs.isNotEmpty(),
            ) {
                HermesSymbol(
                    icon = HermesIcon.Share,
                    contentDescription = stringResource(Res.string.logs_action_export),
                )
            }
            // Likivik patch: upload CrashWatcher app logs (crash/ANR) to Files
            IconButton(onClick = { viewModel.uploadAppLogs() }) {
                HermesSymbol(
                    icon = HermesIcon.FileUpload,
                    contentDescription = stringResource(Res.string.logs_action_upload),
                )
            }
        },
    ) {
        when {
            state.isLoading && state.logs.isEmpty() -> {
                SkeletonListState()
            }

            state.errorMessage != null -> {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = spacing.md, vertical = spacing.sm),
                ) {
                    LogsFilterControls(
                        filters = filters,
                        onFiltersChange = viewModel::setFilters,
                    )
                    ErrorState(
                        message = state.errorMessage ?: stringResource(Res.string.error_unknown),
                        onRetry = { viewModel.loadLogs() },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            state.logs.isEmpty() -> {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = spacing.md, vertical = spacing.sm),
                ) {
                    LogsFilterControls(
                        filters = filters,
                        onFiltersChange = viewModel::setFilters,
                    )
                    EmptyState(
                        title = stringResource(Res.string.logs_empty_title),
                        subtitle = stringResource(Res.string.logs_empty_desc),
                        icon = "history_edu",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            else -> {
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                        contentPadding =
                            PaddingValues(
                                horizontal = spacing.md,
                                vertical = spacing.sm,
                            ),
                    ) {
                        // Search bar
                        item {
                            SearchBar(
                                query = query,
                                onQueryChange = { query = it },
                                placeholder = "Search logs...",
                                modifier = Modifier.padding(bottom = spacing.xs),
                            )
                        }

                        item {
                            LogsFilterControls(
                                filters = filters,
                                onFiltersChange = viewModel::setFilters,
                                modifier = Modifier.padding(bottom = spacing.sm),
                            )
                        }

                        items(filteredLogs) { logLine ->
                            Text(
                                text = logLine,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                            )
                        }
                    }

                    // Jump-to-bottom FAB — only when paused and scrolled up
                    if (pauseScroll && filteredLogs.isNotEmpty()) {
                        FloatingActionButton(
                            onClick = {
                                scope.launch {
                                    listState.animateScrollToItem(filteredLogs.size)
                                }
                            },
                            modifier =
                                Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(spacing.md),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            HermesSymbol(
                                icon = HermesIcon.KeyboardDoubleArrowDown,
                                contentDescription =
                                    stringResource(Res.string.logs_content_desc_jump_bottom),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogsFilterControls(
    filters: LogsFilters,
    onFiltersChange: (LogsFilters) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val fileOptions =
        listOf(
            LogsFilterOption("agent", stringResource(Res.string.logs_filter_agent)),
            LogsFilterOption("errors", stringResource(Res.string.logs_filter_errors)),
            LogsFilterOption("gateway", stringResource(Res.string.logs_filter_gateway)),
        )
    val levelOptions =
        listOf(
            LogsFilterOption("ALL", stringResource(Res.string.logs_filter_all)),
            LogsFilterOption("DEBUG", stringResource(Res.string.logs_filter_debug)),
            LogsFilterOption("INFO", stringResource(Res.string.logs_filter_info)),
            LogsFilterOption("WARNING", stringResource(Res.string.logs_filter_warning)),
            LogsFilterOption("ERROR", stringResource(Res.string.logs_filter_error)),
        )
    val componentOptions =
        listOf(
            LogsFilterOption("all", stringResource(Res.string.logs_filter_all)),
            LogsFilterOption("gateway", stringResource(Res.string.logs_filter_gateway)),
            LogsFilterOption("agent", stringResource(Res.string.logs_filter_agent)),
            LogsFilterOption("tools", stringResource(Res.string.logs_filter_tools)),
            LogsFilterOption("cli", stringResource(Res.string.logs_filter_cli)),
            LogsFilterOption("cron", stringResource(Res.string.logs_filter_cron)),
        )
    val lineOptions = listOf("50", "100", "200", "500").map { LogsFilterOption(it, it) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        FilterChipRow(
            label = stringResource(Res.string.logs_filter_file),
            options = fileOptions,
            selected = filters.file,
            onSelected = { onFiltersChange(filters.copy(file = it)) },
        )
        FilterChipRow(
            label = stringResource(Res.string.logs_filter_level),
            options = levelOptions,
            selected = filters.level,
            onSelected = { onFiltersChange(filters.copy(level = it)) },
        )
        FilterChipRow(
            label = stringResource(Res.string.logs_filter_component),
            options = componentOptions,
            selected = filters.component,
            onSelected = { onFiltersChange(filters.copy(component = it)) },
        )
        FilterChipRow(
            label = stringResource(Res.string.logs_filter_lines),
            options = lineOptions,
            selected = filters.lines.toString(),
            onSelected = { onFiltersChange(filters.copy(lines = it.toInt())) },
        )
    }
}

@Composable
private fun FilterChipRow(
    label: String,
    options: List<LogsFilterOption>,
    selected: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        options.forEach { option ->
            val isSelected = selected == option.value
            FilterChip(
                selected = isSelected,
                onClick = { onSelected(option.value) },
                label = { Text(option.label) },
                leadingIcon =
                    if (isSelected) {
                        {
                            HermesSymbol(
                                icon = HermesIcon.Check,
                                contentDescription = null,
                            )
                        }
                    } else {
                        null
                    },
            )
        }
    }
}
