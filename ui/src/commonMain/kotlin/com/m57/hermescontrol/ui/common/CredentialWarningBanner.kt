package com.m57.hermescontrol.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.m57.hermescontrol.generated.resources.Res
import com.m57.hermescontrol.generated.resources.action_dismiss
import com.m57.hermescontrol.generated.resources.providers_action_fix
import com.m57.hermescontrol.theme.LocalHermesStatusColors
import com.m57.hermescontrol.ui.icons.HermesIcon
import com.m57.hermescontrol.ui.icons.HermesSymbol
import org.jetbrains.compose.resources.stringResource

/**
 * Banner shown when the backend reports a `credential_warning` (the mobile
 * equivalent of the desktop `requestDesktopOnboarding` re-trigger, issue #534).
 * Tapping "Fix" deep-links to ProvidersScreen; dismissing clears it globally.
 */
@Composable
fun CredentialWarningBanner(
    warning: String,
    onFix: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HermesSymbol(
            icon = HermesIcon.Warning,
            contentDescription = null,
            tint = LocalHermesStatusColors.current.warning,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            text = warning,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onFix) {
            Text(
                text = stringResource(Res.string.providers_action_fix),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        TextButton(onClick = onDismiss) {
            Text(
                text = stringResource(Res.string.action_dismiss),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
