package com.m57.hermescontrol

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Webhook
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.NavKey
import com.m57.hermescontrol.generated.resources.Res
import com.m57.hermescontrol.generated.resources.nav_drawer_section_automate
import com.m57.hermescontrol.generated.resources.nav_drawer_section_configure
import com.m57.hermescontrol.generated.resources.nav_drawer_section_converse
import com.m57.hermescontrol.generated.resources.nav_drawer_section_inspect
import com.m57.hermescontrol.generated.resources.screen_achievements
import com.m57.hermescontrol.generated.resources.screen_analytics
import com.m57.hermescontrol.generated.resources.screen_billing
import com.m57.hermescontrol.generated.resources.screen_bots
import com.m57.hermescontrol.generated.resources.screen_channels
import com.m57.hermescontrol.generated.resources.screen_chat
import com.m57.hermescontrol.generated.resources.screen_config
import com.m57.hermescontrol.generated.resources.screen_cron
import com.m57.hermescontrol.generated.resources.screen_files
import com.m57.hermescontrol.generated.resources.screen_gateway
import com.m57.hermescontrol.generated.resources.screen_history
import com.m57.hermescontrol.generated.resources.screen_kanban
import com.m57.hermescontrol.generated.resources.screen_keys
import com.m57.hermescontrol.generated.resources.screen_logs
import com.m57.hermescontrol.generated.resources.screen_mcp_servers
import com.m57.hermescontrol.generated.resources.screen_models
import com.m57.hermescontrol.generated.resources.screen_plugins
import com.m57.hermescontrol.generated.resources.screen_processes
import com.m57.hermescontrol.generated.resources.screen_profiles
import com.m57.hermescontrol.generated.resources.screen_providers
import com.m57.hermescontrol.generated.resources.screen_settings
import com.m57.hermescontrol.generated.resources.screen_skills
import com.m57.hermescontrol.generated.resources.screen_system
import com.m57.hermescontrol.generated.resources.screen_toolsets
import com.m57.hermescontrol.generated.resources.screen_webhooks
import org.jetbrains.compose.resources.StringResource
import com.m57.hermescontrol.ui.achievements.AchievementsScreen as AchievementsScreenContent
import com.m57.hermescontrol.ui.analytics.AnalyticsScreen as AnalyticsScreenContent
import com.m57.hermescontrol.ui.billing.BillingScreen as BillingScreenContent
import com.m57.hermescontrol.ui.bots.BotsScreen as BotsScreenContent
import com.m57.hermescontrol.ui.channels.ChannelsScreen as ChannelsScreenContent
import com.m57.hermescontrol.ui.chat.ChatScreen as ChatScreenContent
import com.m57.hermescontrol.ui.config.ConfigScreen as ConfigScreenContent
import com.m57.hermescontrol.ui.cron.CronJobsScreen as CronJobsScreenContent
import com.m57.hermescontrol.ui.files.FilesScreen as FilesScreenContent
import com.m57.hermescontrol.ui.gateway.GatewayScreen as GatewayScreenContent
import com.m57.hermescontrol.ui.kanban.KanbanScreen as KanbanScreenContent
import com.m57.hermescontrol.ui.keys.KeysScreen as KeysScreenContent
import com.m57.hermescontrol.ui.logs.LogsScreen as LogsScreenContent
import com.m57.hermescontrol.ui.mcp.McpServersScreen as McpServersScreenContent
import com.m57.hermescontrol.ui.model.ModelScreen as ModelScreenContent
import com.m57.hermescontrol.ui.plugins.PluginsScreen as PluginsScreenContent
import com.m57.hermescontrol.ui.process.ProcessesScreen as ProcessesScreenContent
import com.m57.hermescontrol.ui.profiles.ProfilesScreen as ProfilesScreenContent
import com.m57.hermescontrol.ui.providers.ProvidersScreen as ProvidersScreenContent
import com.m57.hermescontrol.ui.sessions.SessionsScreen as HistoryScreenContent
import com.m57.hermescontrol.ui.settings.SettingsScreen as SettingsScreenContent
import com.m57.hermescontrol.ui.skills.SkillsScreen as SkillsScreenContent
import com.m57.hermescontrol.ui.system.SystemScreen as SystemScreenContent
import com.m57.hermescontrol.ui.toolsets.ToolsetsScreen as ToolsetsScreenContent
import com.m57.hermescontrol.ui.webhooks.WebhooksScreen as WebhooksScreenContent

enum class DrawerSection(
    val titleRes: StringResource,
) {
    CONVERSE(Res.string.nav_drawer_section_converse),
    AUTOMATE(Res.string.nav_drawer_section_automate),
    CONFIGURE(Res.string.nav_drawer_section_configure),
    INSPECT(Res.string.nav_drawer_section_inspect),
}

data class ScreenDefinition(
    val key: NavKey,
    val labelRes: StringResource,
    val icon: ImageVector,
    val drawerSection: DrawerSection?,
    val content: @Composable (sessionId: String?, openDrawer: () -> Unit) -> Unit,
)

object ScreenRegistry {
    val ALL_SCREENS =
        listOf(
            ScreenDefinition(
                ChatScreen,
                Res.string.screen_chat,
                Icons.AutoMirrored.Filled.Chat,
                DrawerSection.CONVERSE,
            ) { sessionId, openDrawer -> ChatScreenContent(onOpenDrawer = openDrawer, sessionId = sessionId) },
            ScreenDefinition(
                BotsScreen,
                Res.string.screen_bots,
                Icons.Filled.SmartToy,
                DrawerSection.CONVERSE,
            ) { sessionId, openDrawer -> BotsScreenContent(onOpenDrawer = openDrawer) },
            ScreenDefinition(
                HistoryScreen,
                Res.string.screen_history,
                Icons.Filled.History,
                DrawerSection.CONVERSE,
            ) { sessionId, openDrawer -> HistoryScreenContent(onOpenDrawer = openDrawer) },
            ScreenDefinition(
                ProfilesScreen,
                Res.string.screen_profiles,
                Icons.Filled.AccountCircle,
                DrawerSection.CONVERSE,
            ) { sessionId, openDrawer -> ProfilesScreenContent(onOpenDrawer = openDrawer) },
            ScreenDefinition(
                CronJobsScreen,
                Res.string.screen_cron,
                Icons.Filled.Schedule,
                DrawerSection.AUTOMATE,
            ) { sessionId, openDrawer -> CronJobsScreenContent(onOpenDrawer = openDrawer) },
            ScreenDefinition(
                WebhooksScreen,
                Res.string.screen_webhooks,
                Icons.Filled.Webhook,
                DrawerSection.AUTOMATE,
            ) { sessionId, openDrawer -> WebhooksScreenContent(onOpenDrawer = openDrawer) },
            ScreenDefinition(
                KanbanScreen,
                Res.string.screen_kanban,
                Icons.Filled.Dashboard,
                DrawerSection.AUTOMATE,
            ) { sessionId, openDrawer -> KanbanScreenContent(onOpenDrawer = openDrawer) },
            ScreenDefinition(
                SkillsScreen,
                Res.string.screen_skills,
                Icons.Filled.Extension,
                DrawerSection.CONFIGURE,
            ) { sessionId, openDrawer -> SkillsScreenContent(onOpenDrawer = openDrawer) },
            ScreenDefinition(
                ToolsetsScreen,
                Res.string.screen_toolsets,
                Icons.Filled.Build,
                DrawerSection.CONFIGURE,
            ) { sessionId, openDrawer -> ToolsetsScreenContent(onOpenDrawer = openDrawer) },
            ScreenDefinition(
                PluginsScreen,
                Res.string.screen_plugins,
                Icons.Filled.Memory,
                DrawerSection.CONFIGURE,
            ) { sessionId, openDrawer -> PluginsScreenContent(onOpenDrawer = openDrawer) },
            ScreenDefinition(
                ConfigScreen,
                Res.string.screen_config,
                Icons.Filled.Code,
                DrawerSection.CONFIGURE,
            ) { sessionId, openDrawer -> ConfigScreenContent(onOpenDrawer = openDrawer) },
            ScreenDefinition(
                McpServersScreen,
                Res.string.screen_mcp_servers,
                Icons.Filled.Dashboard,
                DrawerSection.CONFIGURE,
            ) { sessionId, openDrawer -> McpServersScreenContent(onOpenDrawer = openDrawer) },
            ScreenDefinition(
                ModelScreen,
                Res.string.screen_models,
                Icons.Filled.Psychology,
                DrawerSection.CONFIGURE,
            ) { sessionId, openDrawer -> ModelScreenContent(onOpenDrawer = openDrawer) },
            ScreenDefinition(
                KeysScreen,
                Res.string.screen_keys,
                Icons.Filled.Key,
                DrawerSection.CONFIGURE,
            ) { sessionId, openDrawer -> KeysScreenContent(onOpenDrawer = openDrawer) },
            ScreenDefinition(
                ChannelsScreen,
                Res.string.screen_channels,
                Icons.AutoMirrored.Filled.ListAlt,
                DrawerSection.CONFIGURE,
            ) { sessionId, openDrawer -> ChannelsScreenContent(onOpenDrawer = openDrawer) },
            ScreenDefinition(
                ProvidersScreen,
                Res.string.screen_providers,
                Icons.Filled.Cloud,
                DrawerSection.CONFIGURE,
            ) { sessionId, openDrawer -> ProvidersScreenContent(onOpenDrawer = openDrawer) },
            ScreenDefinition(
                GatewayScreen,
                Res.string.screen_gateway,
                Icons.Filled.Bolt,
                DrawerSection.INSPECT,
            ) { sessionId, openDrawer -> GatewayScreenContent(onOpenDrawer = openDrawer) },
            ScreenDefinition(
                SystemScreen,
                Res.string.screen_system,
                Icons.Filled.Info,
                DrawerSection.INSPECT,
            ) { sessionId, openDrawer -> SystemScreenContent(onOpenDrawer = openDrawer) },
            ScreenDefinition(
                LogsScreen,
                Res.string.screen_logs,
                Icons.Filled.HistoryEdu,
                DrawerSection.INSPECT,
            ) { sessionId, openDrawer -> LogsScreenContent(onOpenDrawer = openDrawer) },
            ScreenDefinition(
                ProcessesScreen,
                Res.string.screen_processes,
                Icons.Filled.Memory,
                DrawerSection.INSPECT,
            ) { sessionId, openDrawer -> ProcessesScreenContent(onOpenDrawer = openDrawer) },
            ScreenDefinition(
                AnalyticsScreen,
                Res.string.screen_analytics,
                Icons.Filled.BarChart,
                DrawerSection.INSPECT,
            ) { sessionId, openDrawer -> AnalyticsScreenContent(onOpenDrawer = openDrawer) },
            ScreenDefinition(
                BillingScreen,
                Res.string.screen_billing,
                Icons.Filled.AccountBalanceWallet,
                DrawerSection.INSPECT,
            ) { sessionId, openDrawer -> BillingScreenContent(onOpenDrawer = openDrawer) },
            ScreenDefinition(
                FilesScreen,
                Res.string.screen_files,
                Icons.Filled.Folder,
                DrawerSection.INSPECT,
            ) { sessionId, openDrawer -> FilesScreenContent(onOpenDrawer = openDrawer) },
            ScreenDefinition(
                AchievementsScreen,
                Res.string.screen_achievements,
                Icons.Filled.EmojiEvents,
                DrawerSection.INSPECT,
            ) { sessionId, openDrawer -> AchievementsScreenContent(onOpenDrawer = openDrawer) },
            ScreenDefinition(
                SettingsScreen,
                Res.string.screen_settings,
                Icons.Filled.Settings,
                DrawerSection.INSPECT,
            ) { sessionId, openDrawer ->
                SettingsScreenContent(
                    onOpenDrawer = openDrawer,
                    onNavigateToLogin = {
                        NavigationController.navigateTo(AuthLoginScreen)
                    },
                )
            },
        )
}
