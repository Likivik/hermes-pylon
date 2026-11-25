@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.m57.hermescontrol.ui.files

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.m57.hermescontrol.data.model.ManagedFileEntry
import com.m57.hermescontrol.data.remote.NetworkResult
import com.m57.hermescontrol.generated.resources.Res
import com.m57.hermescontrol.generated.resources.action_cancel
import com.m57.hermescontrol.generated.resources.content_desc_refresh
import com.m57.hermescontrol.generated.resources.files_action_create
import com.m57.hermescontrol.generated.resources.files_action_delete
import com.m57.hermescontrol.generated.resources.files_action_delete_named
import com.m57.hermescontrol.generated.resources.files_action_new_folder
import com.m57.hermescontrol.generated.resources.files_action_up
import com.m57.hermescontrol.generated.resources.files_action_upload
import com.m57.hermescontrol.generated.resources.files_dialog_delete_message
import com.m57.hermescontrol.generated.resources.files_dialog_delete_title
import com.m57.hermescontrol.generated.resources.files_dialog_new_folder_title
import com.m57.hermescontrol.generated.resources.files_empty_desc
import com.m57.hermescontrol.generated.resources.files_empty_title
import com.m57.hermescontrol.generated.resources.files_error_access_forbidden
import com.m57.hermescontrol.generated.resources.files_error_not_found
import com.m57.hermescontrol.generated.resources.files_error_open
import com.m57.hermescontrol.generated.resources.files_error_open_failed
import com.m57.hermescontrol.generated.resources.files_error_read_selected
import com.m57.hermescontrol.generated.resources.files_error_session_expired
import com.m57.hermescontrol.generated.resources.files_error_too_large_to_open
import com.m57.hermescontrol.generated.resources.files_metadata_date_time
import com.m57.hermescontrol.generated.resources.files_metadata_size_and_time
import com.m57.hermescontrol.generated.resources.files_new_folder_label
import com.m57.hermescontrol.generated.resources.files_new_folder_placeholder
import com.m57.hermescontrol.generated.resources.files_root_label
import com.m57.hermescontrol.generated.resources.files_scope_note
import com.m57.hermescontrol.generated.resources.screen_files
import com.m57.hermescontrol.theme.Spacing
import com.m57.hermescontrol.ui.common.EmptyState
import com.m57.hermescontrol.ui.common.ErrorState
import com.m57.hermescontrol.ui.common.HermesIconTile
import com.m57.hermescontrol.ui.common.HermesListItem
import com.m57.hermescontrol.ui.common.HermesScaffold
import com.m57.hermescontrol.ui.common.NavIcon
import com.m57.hermescontrol.ui.common.SecureGatewayMediaPlayer
import com.m57.hermescontrol.ui.common.SecureGatewayMediaRequest
import com.m57.hermescontrol.ui.common.SecureMediaOpenRoute
import com.m57.hermescontrol.ui.common.SkeletonListState
import com.m57.hermescontrol.ui.common.ToastEffect
import com.m57.hermescontrol.ui.common.routeManagedFileOpen
import com.m57.hermescontrol.ui.icons.HermesIcon
import com.m57.hermescontrol.ui.icons.HermesSymbol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

private val filesContentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
private val filesItemSpacing = Arrangement.spacedBy(8.dp)

/**
 * Resolves a [FilesUiText] inside a `@Composable` by reading the
 * underlying `StringResource` key through Compose's resource pipeline.
 * Substitutes the captured [FilesUiText.args] the same way
 * `Context.getString(Int, vararg)` did, so `%1$s` / `%2$d` placeholders
 * line up with the original `R.string.*` definitions.
 */
@Composable
private fun filesText(text: FilesUiText): String =
    if (text.args.isEmpty()) {
        stringResource(text.key)
    } else {
        stringResource(text.key, *text.args.toTypedArray())
    }

/**
 * What the Files screen can do. [FilesRoute] implements this over the ViewModel; the screen
 * takes it so it can render from state alone, which is what makes it testable.
 */
interface FilesActions {
    fun refresh()

    fun navigateTo(entry: ManagedFileEntry)

    fun navigateUp()

    fun navigateToCrumb(path: String)

    fun openCreateDir()

    fun dismissCreateDir()

    fun setNewDirName(name: String)

    fun createDir()

    fun requestDelete(entry: ManagedFileEntry)

    fun dismissDelete()

    fun confirmDelete()

    fun uploadFile(
        name: String,
        bytes: ByteArray,
        mimeType: String,
    )

    fun downloadFile(
        entry: ManagedFileEntry,
        staging: String,
        onResult: (NetworkResult<DownloadedFile>) -> Unit,
    )

    fun showToast(message: FilesUiText)

    fun clearToast()

    fun clearTransientState()
}

@Composable
fun FilesRoute(
    modifier: Modifier = Modifier,
    onOpenDrawer: (() -> Unit)? = null,
    viewModel: FilesViewModel = viewModel { FilesViewModel() },
    launchDownloadedFile: ((DownloadedFile) -> Unit)? = rememberDownloadedFileLauncher(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.refresh()
    }
    DisposableEffect(Unit) {
        onDispose { viewModel.clearTransientState() }
    }
    val actions =
        remember(viewModel) {
            object : FilesActions {
                override fun refresh() = viewModel.refresh()

                override fun navigateTo(entry: ManagedFileEntry) = viewModel.navigateTo(entry)

                override fun navigateUp() = viewModel.navigateUp()

                override fun navigateToCrumb(path: String) = viewModel.navigateToCrumb(path)

                override fun openCreateDir() = viewModel.openCreateDir()

                override fun dismissCreateDir() = viewModel.dismissCreateDir()

                override fun setNewDirName(name: String) = viewModel.setNewDirName(name)

                override fun createDir() = viewModel.createDir()

                override fun requestDelete(entry: ManagedFileEntry) = viewModel.requestDelete(entry)

                override fun dismissDelete() = viewModel.dismissDelete()

                override fun confirmDelete() = viewModel.confirmDelete()

                override fun uploadFile(
                    name: String,
                    bytes: ByteArray,
                    mimeType: String,
                ) = viewModel.uploadFile(name, bytes, mimeType)

                override fun downloadFile(
                    entry: ManagedFileEntry,
                    staging: String,
                    onResult: (NetworkResult<DownloadedFile>) -> Unit,
                ) = viewModel.downloadFile(entry, staging, onResult)

                override fun showToast(message: FilesUiText) = viewModel.showToast(message)

                override fun clearToast() = viewModel.clearToast()

                override fun clearTransientState() = viewModel.clearTransientState()
            }
        }
    FilesScreen(
        state = state,
        actions = actions,
        modifier = modifier,
        onOpenDrawer = onOpenDrawer,
        launchDownloadedFile = launchDownloadedFile,
    )
}

@Composable
fun FilesScreen(
    state: FilesUiState,
    actions: FilesActions,
    modifier: Modifier = Modifier,
    onOpenDrawer: (() -> Unit)? = null,
    launchDownloadedFile: ((DownloadedFile) -> Unit)? = null,
    filesPlatform: FilesPlatform = rememberFilesPlatform(),
) {
    val scope = rememberCoroutineScope()
    var mediaPlayerRequest by remember { mutableStateOf<SecureGatewayMediaRequest?>(null) }

    val uploadFile: () -> Unit = {
        filesPlatform.pickFile(
            mimeType = "*/*",
            onPicked = { picked ->
                scope.launch { actions.uploadFile(picked.name, picked.bytes, picked.mimeType) }
            },
            onError = { message ->
                if (message != null) {
                    actions.showToast(
                        FilesUiText(Res.string.files_error_read_selected, listOf(message)),
                    )
                }
            },
        )
    }

    val hasEntries = state.entries.isNotEmpty()

    ToastEffect(
        toastMessage = state.toastMessage?.let { filesText(it) },
        onClearToast = actions::clearToast,
    )

    // ── Create-directory dialog ────────────────────────────────────────
    if (state.isCreatingDir) {
        CreateDirDialog(
            name = state.newDirName,
            onNameChange = actions::setNewDirName,
            onConfirm = actions::createDir,
            onDismiss = actions::dismissCreateDir,
        )
    }

    // ── Delete confirmation dialog ─────────────────────────────────────
    state.deleteTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = actions::dismissDelete,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HermesSymbol(
                        icon = HermesIcon.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(Res.string.files_dialog_delete_title))
                }
            },
            text = {
                Text(
                    stringResource(
                        Res.string.files_dialog_delete_message,
                        entry.name,
                    ),
                )
            },
            confirmButton = {
                Button(
                    onClick = actions::confirmDelete,
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                        ),
                ) {
                    Text(stringResource(Res.string.files_action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = actions::dismissDelete) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }

    HermesScaffold(
        title = {
            Column {
                Text(stringResource(Res.string.screen_files))
                // Files is the gateway process's own home: a profile switch cannot move it.
                Text(
                    text = stringResource(Res.string.files_scope_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        navigationIcon = onOpenDrawer?.let { NavIcon.Menu(it) },
        isRefreshing = state.isLoading,
        onRefresh = actions::refresh,
        actions = {
            if (state.parentPath != null) {
                val upLabel = stringResource(Res.string.files_action_up)
                IconButton(
                    onClick = actions::navigateUp,
                    modifier = Modifier.semantics { contentDescription = upLabel },
                ) {
                    HermesSymbol(icon = HermesIcon.ArrowUpward, contentDescription = upLabel)
                }
            }
            val uploadLabel = stringResource(Res.string.files_action_upload)
            IconButton(
                onClick = uploadFile,
                modifier =
                    Modifier
                        .semantics { contentDescription = uploadLabel }
                        .testTag("files-upload"),
            ) {
                HermesSymbol(
                    icon = HermesIcon.Upload,
                    contentDescription = stringResource(Res.string.files_action_upload),
                )
            }
        },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading && !hasEntries -> {
                    SkeletonListState(modifier = Modifier.padding(paddingValues))
                }

                state.errorMessage != null && !hasEntries -> {
                    ErrorState(
                        message = state.errorMessage?.let { filesText(it) }.orEmpty(),
                        onRetry = actions::refresh,
                        modifier = Modifier.padding(paddingValues),
                    )
                }

                !hasEntries -> {
                    EmptyState(
                        title = stringResource(Res.string.files_empty_title),
                        subtitle = stringResource(Res.string.files_empty_desc),
                        onAction = actions::refresh,
                        actionLabel = stringResource(Res.string.content_desc_refresh),
                        modifier = Modifier.padding(paddingValues),
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = filesContentPadding,
                        verticalArrangement = filesItemSpacing,
                    ) {
                        // ── Breadcrumb bar ────────────────────────────────
                        item(key = "breadcrumb") {
                            BreadcrumbBar(
                                crumbs = state.crumbs,
                                onCrumbClick = actions::navigateToCrumb,
                            )
                        }

                        // ── Up row (when not at root) ─────────────────────
                        if (state.parentPath != null) {
                            item(key = "up") {
                                FileRow(
                                    icon = HermesIcon.ArrowUpward,
                                    name = "..",
                                    subtitle = stringResource(Res.string.files_action_up),
                                    isBusy = false,
                                    onClick = actions::navigateUp,
                                )
                            }
                        }

                        // ── Entries ───────────────────────────────────────
                        items(
                            items = state.entries,
                            key = { it.path },
                        ) { entry ->
                            FileRow(
                                entry = entry,
                                isBusy = entry.path in state.busyPaths || state.openingPath == entry.path,
                                onClick = {
                                    if (entry.isDirectory) {
                                        actions.navigateTo(entry)
                                    } else {
                                        when (val route = routeManagedFileOpen(entry)) {
                                            is SecureMediaOpenRoute.Player -> mediaPlayerRequest = route.request
                                            SecureMediaOpenRoute.DownloadAndOpen ->
                                                openManagedFile(
                                                    entry,
                                                    actions,
                                                    filesPlatform,
                                                    scope,
                                                    launchDownloadedFile,
                                                )

                                            SecureMediaOpenRoute.OpenLocal -> Unit
                                        }
                                    }
                                },
                                onDelete = { actions.requestDelete(entry) },
                            )
                        }
                    }
                }
            }

            val newFolderLabel = stringResource(Res.string.files_action_new_folder)
            FloatingActionButton(
                onClick = actions::openCreateDir,
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(Spacing.md)
                        .semantics { contentDescription = newFolderLabel },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                HermesSymbol(icon = HermesIcon.CreateNewFolder, contentDescription = newFolderLabel)
            }
        }
    }

    mediaPlayerRequest?.let { request ->
        SecureGatewayMediaPlayer(request = request, onClose = { mediaPlayerRequest = null })
    }
}

// ── Breadcrumb bar ───────────────────────────────────────────────────────────

@Composable
private fun BreadcrumbBar(
    crumbs: List<Pair<String, String>>,
    onCrumbClick: (String) -> Unit,
) {
    if (crumbs.isEmpty()) {
        Text(
            text = stringResource(Res.string.files_root_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.xs),
        )
        return
    }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        crumbs.forEachIndexed { index, (path, name) ->
            if (index > 0) {
                Text(
                    text = "/",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .clickable { onCrumbClick(path) }
                        .padding(horizontal = 2.dp),
            )
        }
    }
}

// ── File row ─────────────────────────────────────────────────────────────────

@Composable
private fun FileRow(
    entry: ManagedFileEntry? = null,
    icon: HermesIcon = entry?.let { fileIcon(it) } ?: HermesIcon.Folder,
    name: String = entry?.name ?: "",
    subtitle: String? = null,
    isBusy: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val resolvedSubtitle = subtitle ?: entry?.let { fileSubtitle(it) }
    HermesListItem(
        onClick = if (isBusy) null else onClick,
        leading = {
            HermesIconTile(icon = icon)
        },
        headline = {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supporting =
            resolvedSubtitle?.let { sub ->
                {
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
        trailing = {
            when {
                isBusy ->
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )

                onDelete != null && entry != null && !entry.isDirectory -> {
                    val deleteLabel = stringResource(Res.string.files_action_delete_named, entry.name)
                    IconButton(
                        onClick = onDelete,
                        modifier =
                            Modifier.size(40.dp).semantics {
                                contentDescription = deleteLabel
                            },
                    ) {
                        HermesSymbol(
                            icon = HermesIcon.Delete,
                            contentDescription = deleteLabel,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                else -> Unit
            }
        },
        containerColor = Color.Transparent,
    )
}

// ── Create directory dialog ──────────────────────────────────────────────────

@Composable
private fun CreateDirDialog(
    name: String,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.files_dialog_new_folder_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(Res.string.files_new_folder_label)) },
                placeholder = { Text(stringResource(Res.string.files_new_folder_placeholder)) },
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = name.isNotBlank(),
            ) {
                Text(stringResource(Res.string.files_action_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
}

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun fileIcon(entry: ManagedFileEntry): HermesIcon =
    when {
        entry.isDirectory -> HermesIcon.Folder
        entry.mimeType?.startsWith("image/") == true -> HermesIcon.Image
        entry.mimeType?.startsWith("text/") == true -> HermesIcon.Description
        else -> HermesIcon.AttachFile
    }

@Composable
private fun fileSubtitle(entry: ManagedFileEntry): String {
    val platform = rememberFilesPlatform()
    val size = entry.size?.let { platform.formatSize(it) } ?: "—"
    val mtime =
        entry.mtime
            ?.takeIf { it.isFinite() && it >= 0.0 }
            ?.let { seconds ->
                val (date, time) = platform.formatDateTime((seconds * 1000.0).toLong())
                stringResource(Res.string.files_metadata_date_time, date, time)
            }
    return if (mtime == null) {
        size
    } else {
        stringResource(Res.string.files_metadata_size_and_time, size, mtime)
    }
}

/**
 * Download a managed file **in-app** through the authenticated client and open
 * it on the device through the platform's file opener.
 *
 * Fetching through [FilesViewModel.downloadFile] reuses the normal
 * Bearer/session-cookie client. The file is materialized locally before
 * viewing, so credentials never leave the app in a browser URL.
 */
private fun openManagedFile(
    entry: ManagedFileEntry,
    actions: FilesActions,
    platform: FilesPlatform,
    scope: CoroutineScope,
    launchDownloadedFile: ((DownloadedFile) -> Unit)? = null,
) {
    val staging = platform.stagingDirectory()
    if (staging == null && launchDownloadedFile == null) {
        actions.showToast(FilesUiText(Res.string.files_error_open, emptyList()))
        return
    }
    actions.downloadFile(entry, staging.orEmpty()) { result ->
        when (result) {
            is com.m57.hermescontrol.data.remote.NetworkResult.Success -> {
                val data = result.data
                if (launchDownloadedFile != null) {
                    launchDownloadedFile(data)
                    return@downloadFile
                }
                scope.launch {
                    platform
                        .openDownloaded(
                            cachePath = data.cachePath ?: error("no cache path for ${data.name}"),
                            name = data.name,
                            mimeType = data.mimeType,
                        ).onFailure {
                            actions.showToast(
                                FilesUiText(
                                    Res.string.files_error_open,
                                    listOf(it.message.orEmpty()),
                                ),
                            )
                        }
                }
            }

            is com.m57.hermescontrol.data.remote.NetworkResult.Failure -> {
                val message =
                    when (val error = result.error) {
                        is com.m57.hermescontrol.data.remote.NetworkError.AuthExpired ->
                            FilesUiText(Res.string.files_error_session_expired)

                        is com.m57.hermescontrol.data.remote.NetworkError.Http ->
                            when (error.code) {
                                403 -> FilesUiText(Res.string.files_error_access_forbidden)
                                404 -> FilesUiText(Res.string.files_error_not_found)
                                413 -> FilesUiText(Res.string.files_error_too_large_to_open)
                                else ->
                                    FilesUiText(
                                        Res.string.files_error_open_failed,
                                        listOf(error.message),
                                    )
                            }

                        else ->
                            FilesUiText(
                                Res.string.files_error_open_failed,
                                listOf(error.message),
                            )
                    }
                actions.showToast(message)
            }
        }
    }
}
