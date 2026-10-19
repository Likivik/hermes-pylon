package com.m57.hermescontrol.ui.skills.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.m57.hermescontrol.generated.resources.Res
import com.m57.hermescontrol.generated.resources.action_cancel
import com.m57.hermescontrol.generated.resources.action_discard
import com.m57.hermescontrol.generated.resources.content_desc_save_changes
import com.m57.hermescontrol.generated.resources.skills_content_placeholder
import com.m57.hermescontrol.generated.resources.skills_discard_message
import com.m57.hermescontrol.generated.resources.skills_discard_title
import com.m57.hermescontrol.generated.resources.skills_edit_title
import com.m57.hermescontrol.theme.Spacing
import com.m57.hermescontrol.ui.common.HermesScaffold
import com.m57.hermescontrol.ui.common.LoadingState
import com.m57.hermescontrol.ui.common.NavIcon
import com.m57.hermescontrol.ui.icons.HermesIcon
import com.m57.hermescontrol.ui.icons.HermesSymbol
import org.jetbrains.compose.resources.stringResource

@Composable
fun SkillEditorDialog(
    skillName: String,
    isLoading: Boolean,
    initialContent: String?,
    isSaving: Boolean,
    saveSuccess: Boolean,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    onClearSaveSuccess: () -> Unit,
) {
    var contentText by remember(initialContent) { mutableStateOf(initialContent.orEmpty()) }
    var showDiscardConfirm by remember { mutableStateOf(false) }

    val hasChanges =
        remember(initialContent, contentText) {
            val original = initialContent.orEmpty()
            original != contentText
        }

    LaunchedEffect(saveSuccess) {
        if (saveSuccess) {
            onClearSaveSuccess()
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = {
            if (hasChanges) {
                showDiscardConfirm = true
            } else {
                onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(Spacing.md),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
        ) {
            HermesScaffold(
                title = { Text(stringResource(Res.string.skills_edit_title, skillName)) },
                navigationIcon =
                    NavIcon.Back(
                        onBack = {
                            if (hasChanges) {
                                showDiscardConfirm = true
                            } else {
                                onDismiss()
                            }
                        },
                    ),
                actions = {
                    if (!isLoading) {
                        IconButton(
                            onClick = { onSave(contentText) },
                            enabled = !isSaving,
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.fillMaxSize(0.6f),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            } else {
                                HermesSymbol(
                                    icon = HermesIcon.Save,
                                    contentDescription = stringResource(Res.string.content_desc_save_changes),
                                )
                            }
                        }
                    }
                },
            ) { padding ->
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize(),
                ) {
                    when {
                        isLoading -> {
                            LoadingState()
                        }

                        else -> {
                            OutlinedTextField(
                                value = contentText,
                                onValueChange = { contentText = it },
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .padding(Spacing.md),
                                textStyle =
                                    TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    ),
                                placeholder = {
                                    Text(stringResource(Res.string.skills_content_placeholder))
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text(stringResource(Res.string.skills_discard_title)) },
            text = { Text(stringResource(Res.string.skills_discard_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardConfirm = false
                        onDismiss()
                    },
                ) {
                    Text(stringResource(Res.string.action_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            },
        )
    }
}
