package com.m57.hermescontrol.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.m57.hermescontrol.NavigationController
import com.m57.hermescontrol.SettingsAbout
import com.m57.hermescontrol.SettingsAppearance
import com.m57.hermescontrol.SettingsBehavior
import com.m57.hermescontrol.SettingsChat
import com.m57.hermescontrol.SettingsConnection
import com.m57.hermescontrol.SettingsLanguage
import com.m57.hermescontrol.data.theme.ThemePreference
import com.m57.hermescontrol.generated.resources.Res
import com.m57.hermescontrol.generated.resources.screen_settings
import com.m57.hermescontrol.generated.resources.settings_sec_about
import com.m57.hermescontrol.generated.resources.settings_sec_appearance
import com.m57.hermescontrol.generated.resources.settings_sec_behavior
import com.m57.hermescontrol.generated.resources.settings_sec_chat
import com.m57.hermescontrol.generated.resources.settings_sec_connection
import com.m57.hermescontrol.generated.resources.settings_sec_language
import com.m57.hermescontrol.generated.resources.settings_summary_off
import com.m57.hermescontrol.generated.resources.settings_summary_on
import com.m57.hermescontrol.generated.resources.settings_summary_profiles
import com.m57.hermescontrol.generated.resources.settings_summary_theme_dark
import com.m57.hermescontrol.generated.resources.settings_summary_theme_light
import com.m57.hermescontrol.generated.resources.settings_summary_theme_system
import com.m57.hermescontrol.generated.resources.settings_summary_typing_off
import com.m57.hermescontrol.generated.resources.settings_summary_typing_on
import com.m57.hermescontrol.theme.Spacing
import com.m57.hermescontrol.ui.common.HermesScaffold
import com.m57.hermescontrol.ui.common.NavIcon
import com.m57.hermescontrol.ui.icons.HermesIcon
import com.m57.hermescontrol.ui.icons.HermesSymbol
import com.m57.hermescontrol.ui.settings.components.languageLabel
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingsScreen(
    onOpenDrawer: (() -> Unit)? = null,
    onNavigateToLogin: () -> Unit = {},
    onLogout: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel { SettingsViewModel() },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    HermesScaffold(
        title = { Text(stringResource(Res.string.screen_settings)) },
        navigationIcon = onOpenDrawer?.let { NavIcon.Menu(it) },
    ) {
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        ) {
            SettingsCategoryCard(
                items =
                    listOf(
                        SettingsRow(
                            icon = HermesIcon.Language,
                            label = stringResource(Res.string.settings_sec_language),
                            summary = languageLabel(state.appLanguage),
                            onClick = { NavigationController.navigateTo(SettingsLanguage) },
                        ),
                        SettingsRow(
                            icon = HermesIcon.ListAlt,
                            label = stringResource(Res.string.settings_sec_connection),
                            summary =
                                if (state.profiles.isNotEmpty()) {
                                    stringResource(
                                        Res.string.settings_summary_profiles,
                                        state.profiles.size,
                                    )
                                } else {
                                    null
                                },
                            onClick = { NavigationController.navigateTo(SettingsConnection) },
                        ),
                        SettingsRow(
                            icon = HermesIcon.Palette,
                            label = stringResource(Res.string.settings_sec_appearance),
                            summary = appearanceSummary(state),
                            onClick = { NavigationController.navigateTo(SettingsAppearance) },
                        ),
                        SettingsRow(
                            icon = HermesIcon.ChatBubbleOutline,
                            label = stringResource(Res.string.settings_sec_chat),
                            summary =
                                if (state.typingEffectEnabled) {
                                    stringResource(Res.string.settings_summary_typing_on)
                                } else {
                                    stringResource(Res.string.settings_summary_typing_off)
                                },
                            onClick = { NavigationController.navigateTo(SettingsChat) },
                        ),
                        SettingsRow(
                            icon = HermesIcon.Tune,
                            label = stringResource(Res.string.settings_sec_behavior),
                            summary =
                                if (state.autoReconnect) {
                                    stringResource(Res.string.settings_summary_on)
                                } else {
                                    stringResource(Res.string.settings_summary_off)
                                },
                            onClick = { NavigationController.navigateTo(SettingsBehavior) },
                        ),
                        SettingsRow(
                            icon = HermesIcon.Info,
                            label = stringResource(Res.string.settings_sec_about),
                            onClick = { NavigationController.navigateTo(SettingsAbout) },
                        ),
                    ),
            )
        }
    }
}

@Composable
private fun appearanceSummary(state: SettingsUiState): String =
    when (state.themePreference) {
        ThemePreference.SYSTEM -> stringResource(Res.string.settings_summary_theme_system)
        ThemePreference.LIGHT -> stringResource(Res.string.settings_summary_theme_light)
        ThemePreference.DARK -> stringResource(Res.string.settings_summary_theme_dark)
    }

private data class SettingsRow(
    val icon: HermesIcon,
    val label: String,
    val summary: String? = null,
    val onClick: () -> Unit,
)

@Composable
private fun SettingsCategoryCard(items: List<SettingsRow>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column {
            items.forEachIndexed { index, row ->
                ListItem(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClick = row.onClick),
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = {
                        HermesSymbol(
                            icon = row.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    headlineContent = {
                        Text(
                            text = row.label,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    supportingContent =
                        row.summary?.let { summary ->
                            {
                                Text(
                                    text = summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    trailingContent = {
                        HermesSymbol(
                            icon = HermesIcon.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
                if (index < items.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp, end = Spacing.md),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }
    }
}
