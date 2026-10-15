package com.m57.hermescontrol.ui.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.m57.hermescontrol.R
import com.m57.hermescontrol.theme.LocalHermesStatusColors
import com.m57.hermescontrol.ui.chat.VaultPromptUi

@Composable
fun VaultPromptDialog(
    prompt: VaultPromptUi,
    onConfirm: (String) -> Unit,
    onConfirmLogin: (String, String) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember(prompt.binding, prompt.method) { mutableStateOf("") }
    var identifier by remember(prompt.binding, prompt.method) { mutableStateOf(prompt.identifier.orEmpty()) }
    val saveLogin = prompt.method == "vault.save_login"
    AlertDialog(
        onDismissRequest = { if (!prompt.isSubmitting) onDismiss() },
        title = {
            Text(
                prompt.title ?: when (prompt.method) {
                    "vault.code" -> "Verification code required"
                    "vault.unlock_prompt" -> "Unlock vault"
                    else -> "Save login"
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                prompt.prompt?.takeIf(String::isNotBlank)?.let { Text(it) }
                if (saveLogin) {
                    Text(
                        text = "Requested site: ${prompt.requestedOrigin ?: "Missing origin"}",
                        modifier = Modifier.testTag("vault_requested_origin"),
                    )
                    if (prompt.hasValidRequestedOrigin) {
                        OutlinedTextField(
                            value = identifier,
                            onValueChange = { identifier = it },
                            label = { Text("Identifier") },
                            modifier = Modifier.fillMaxWidth().testTag("vault_identifier_input"),
                            enabled = !prompt.isSubmitting,
                        )
                    } else {
                        Text("This request has no valid web origin. Cancel it without entering credentials.")
                    }
                }
                if (!saveLogin || prompt.hasValidRequestedOrigin) {
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        label = { Text(if (prompt.method == "vault.code") "Code" else "Password") },
                        modifier = Modifier.fillMaxWidth().testTag("vault_secret_input"),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions =
                            KeyboardOptions(
                                autoCorrectEnabled = false,
                                keyboardType = KeyboardType.Password,
                            ),
                        enabled = !prompt.isSubmitting,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (saveLogin) onConfirmLogin(identifier, value) else onConfirm(value) },
                enabled =
                    value.isNotBlank() &&
                        (!saveLogin || (prompt.hasValidRequestedOrigin && identifier.isNotBlank())) &&
                        !prompt.isSubmitting,
                modifier = Modifier.testTag("vault_send_button"),
            ) { Text(stringResource(R.string.chat_send)) }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                enabled = !prompt.isSubmitting,
                modifier = Modifier.testTag("vault_cancel_button"),
            ) {
                Text(stringResource(R.string.chat_privileged_cancel))
            }
        },
    )
}

/**
 * Secure password dialog for a pending `sudo.request` (issue #524).
 * The backend blocked the turn waiting for the sudo password — previously
 * mobile dropped the event and the agent hung forever.
 *
 * [onDismiss] (back gesture / outside tap) is an incidental gesture and must
 * stay a no-op; only [onCancel] tells the gateway anything. The typed password
 * lives in this composition and in the single call [onConfirm] makes — it is
 * never lifted into ViewModel state, logs, or errors.
 */
@Composable
fun SudoPromptDialog(
    binding: Any,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    isSubmitting: Boolean = false,
) {
    var password by remember(binding) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text(stringResource(R.string.chat_sudo_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = stringResource(R.string.chat_sudo_body))
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.chat_sudo_password)) },
                    modifier = Modifier.fillMaxWidth().testTag("sudo_password_input"),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
                    enabled = !isSubmitting,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (password.isNotBlank()) {
                        onConfirm(password)
                    }
                },
                enabled = password.isNotBlank() && !isSubmitting,
                modifier = Modifier.testTag("sudo_send_button"),
            ) {
                Text(stringResource(R.string.chat_send))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.testTag("sudo_cancel_button"),
                enabled = !isSubmitting,
            ) {
                Text(stringResource(R.string.chat_privileged_cancel))
            }
        },
    )
}

/**
 * Secure value dialog for a pending `secret.request` (issue #524).
 * The backend blocked the turn waiting for a secret (token/password) —
 * previously mobile dropped the event and the agent hung forever.
 *
 * Same contract as [SudoPromptDialog]: dismissal is a no-op, [onCancel] is the
 * only cancellation, and the entered value never leaves this composition except
 * through [onConfirm].
 */
@Composable
fun SecretPromptDialog(
    binding: Any,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
    envVar: String? = null,
    prompt: String? = null,
    isSubmitting: Boolean = false,
) {
    var secret by remember(binding) { mutableStateOf("") }
    val titleText = envVar?.takeIf { it.isNotBlank() } ?: stringResource(R.string.chat_secret_title)
    val bodyText = prompt?.takeIf { it.isNotBlank() } ?: stringResource(R.string.chat_secret_body)
    val labelText = envVar?.takeIf { it.isNotBlank() } ?: stringResource(R.string.chat_secret_value)

    AlertDialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        title = { Text(titleText) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = bodyText)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = secret,
                    onValueChange = { secret = it },
                    label = { Text(labelText) },
                    modifier = Modifier.fillMaxWidth().testTag("secret_value_input"),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
                    enabled = !isSubmitting,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (secret.isNotBlank()) {
                        onConfirm(secret)
                    }
                },
                enabled = secret.isNotBlank() && !isSubmitting,
                modifier = Modifier.testTag("secret_send_button"),
            ) {
                Text(stringResource(R.string.chat_send))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.testTag("secret_cancel_button"),
                enabled = !isSubmitting,
            ) {
                Text(stringResource(R.string.chat_privileged_cancel))
            }
        },
    )
}

/** Re-authentication dialog shown when the connection enters AUTH_EXPIRED. */
@Composable
fun ReloginDialog(
    onDismiss: () -> Unit,
    onRelogin: (String, String, (Boolean, String?) -> Unit) -> Unit,
) {
    val emptyCredentialsError = stringResource(R.string.chat_relogin_error_empty)
    val statusColors = LocalHermesStatusColors.current
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text(stringResource(R.string.chat_relogin_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.chat_relogin_username)) },
                    singleLine = true,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        errorMessage = null
                    },
                    label = { Text(stringResource(R.string.chat_relogin_password)) },
                    singleLine = true,
                    enabled = !isLoading,
                    visualTransformation =
                        androidx.compose.ui.text.input
                            .PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = statusColors.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isLoading,
                onClick = {
                    if (username.isBlank() || password.isBlank()) {
                        errorMessage = emptyCredentialsError
                        return@TextButton
                    }
                    isLoading = true
                    errorMessage = null
                    onRelogin(username, password) { success, error ->
                        isLoading = false
                        if (success) {
                            onDismiss()
                        } else {
                            errorMessage = error ?: "Unknown error"
                        }
                    }
                },
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = statusColors.info,
                    )
                } else {
                    Text(stringResource(R.string.chat_relogin_submit))
                }
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isLoading,
                onClick = onDismiss,
            ) {
                Text(stringResource(R.string.chat_relogin_cancel))
            }
        },
    )
}
