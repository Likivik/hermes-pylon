package com.m57.hermescontrol.ui.settings.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.m57.hermescontrol.BuildFlags
import com.m57.hermescontrol.generated.resources.Res
import com.m57.hermescontrol.generated.resources.settings_about_app_name
import com.m57.hermescontrol.generated.resources.settings_about_build
import com.m57.hermescontrol.generated.resources.settings_about_commit
import com.m57.hermescontrol.generated.resources.settings_about_debug
import com.m57.hermescontrol.generated.resources.settings_about_release
import com.m57.hermescontrol.generated.resources.settings_about_version
import com.m57.hermescontrol.ui.settings.InfoRow
import com.m57.hermescontrol.ui.settings.SectionCard
import org.jetbrains.compose.resources.stringResource

@Composable
fun AboutSection() {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.settings_about_app_name),
                style =
                    MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                    ),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(8.dp))

        InfoRow(
            label = stringResource(Res.string.settings_about_version),
            value = "v${BuildFlags.versionName} (${BuildFlags.versionCode})",
        )
        InfoRow(
            label = stringResource(Res.string.settings_about_build),
            value =
                if (BuildFlags.isDebug) {
                    stringResource(Res.string.settings_about_debug)
                } else {
                    stringResource(Res.string.settings_about_release)
                },
        )
        if (BuildFlags.gitSha.isNotBlank()) {
            InfoRow(
                label = stringResource(Res.string.settings_about_commit),
                value = BuildFlags.gitSha,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "https://github.com/Hy4ri/hermes-mobile",
            style =
                MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.primary,
                ),
        )
    }
}
