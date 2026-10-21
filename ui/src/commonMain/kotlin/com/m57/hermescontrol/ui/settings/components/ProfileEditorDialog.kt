// Modified from Hy4ri/hermes-mobile for this fork; see NOTICE.

package com.m57.hermescontrol.ui.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.m57.hermescontrol.generated.resources.Res
import com.m57.hermescontrol.generated.resources.action_cancel
import com.m57.hermescontrol.generated.resources.action_delete
import com.m57.hermescontrol.generated.resources.action_save
import com.m57.hermescontrol.generated.resources.auth_login_base_url_label
import com.m57.hermescontrol.generated.resources.settings_delete_confirm_message
import com.m57.hermescontrol.generated.resources.settings_delete_confirm_title
import com.m57.hermescontrol.generated.resources.settings_field_profile_name
import com.m57.hermescontrol.generated.resources.settings_field_token
import com.m57.hermescontrol.generated.resources.settings_title_add_profile
import com.m57.hermescontrol.generated.resources.settings_title_edit_profile
import org.jetbrains.compose.resources.stringResource

@Composable
fun ProfileEditorDialog(
    isEditing: Boolean,
    name: String,
    baseUrl: String,
    transportWarning: String?,
    errorMessage: String?,
    token: String,
    onNameChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val title =
        if (isEditing) {
            stringResource(Res.string.settings_title_edit_profile)
        } else {
            stringResource(Res.string.settings_title_add_profile)
        }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(Res.string.settings_field_profile_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = onBaseUrlChange,
                    label = {
                        Text(stringResource(Res.string.auth_login_base_url_label))
                    },
                    placeholder = {
                        Text("https://hermes.example.com:9119")
                    },
                    singleLine = true,
                    keyboardOptions =
                        KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                transportWarning?.let { warning ->
                    Text(
                        text = warning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                errorMessage?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (isEditing) {
                    OutlinedTextField(
                        value = token,
                        onValueChange = onTokenChange,
                        label = { Text(stringResource(Res.string.settings_field_token)) },
                        singleLine = true,
                        visualTransformation =
                            if (token.isNotEmpty()) {
                                PasswordVisualTransformation()
                            } else {
                                VisualTransformation.None
                            },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = name.isNotBlank() && baseUrl.isNotBlank(),
            ) {
                Text(stringResource(Res.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
}

@Composable
fun DeleteProfileConfirmDialog(
    profileName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_delete_confirm_title)) },
        text = {
            Text(stringResource(Res.string.settings_delete_confirm_message, profileName))
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(Res.string.action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.action_cancel))
            }
        },
    )
}
