package com.m57.hermescontrol

import com.m57.hermescontrol.generated.resources.Res
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
import com.m57.hermescontrol.ui.chat.AndroidChatScreen
import com.m57.hermescontrol.ui.icons.HermesIcon
import com.m57.hermescontrol.ui.achievements.AchievementsScreen as AchievementsScreenContent
import com.m57.hermescontrol.ui.analytics.AnalyticsScreen as AnalyticsScreenContent
import com.m57.hermescontrol.ui.billing.BillingScreen as BillingScreenContent
import com.m57.hermescontrol.ui.bots.BotsScreen as BotsScreenContent
import com.m57.hermescontrol.ui.channels.ChannelsScreen as ChannelsScreenContent
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

/**
 * Android assembly of [screenGraph]. Only the list is platform-specific: the
 * chat entry builds `ChatViewModel` from the `Application`, which no other
 * target can do.
 */
actual fun screenGraph(): ScreenGraph =
    ScreenGraph(
        screens =
            listOf(
                ScreenDefinition(
                    ChatScreen,
                    Res.string.screen_chat,
                    HermesIcon.Chat,
                    DrawerSection.CONVERSE,
                ) { sessionId, openDrawer -> AndroidChatScreen(onOpenDrawer = openDrawer, sessionId = sessionId) },
                ScreenDefinition(
                    BotsScreen,
                    Res.string.screen_bots,
                    HermesIcon.SmartToy,
                    DrawerSection.CONVERSE,
                ) { sessionId, openDrawer -> BotsScreenContent(onOpenDrawer = openDrawer) },
                ScreenDefinition(
                    HistoryScreen,
                    Res.string.screen_history,
                    HermesIcon.History,
                    DrawerSection.CONVERSE,
                ) { sessionId, openDrawer -> HistoryScreenContent(onOpenDrawer = openDrawer) },
                ScreenDefinition(
                    ProfilesScreen,
                    Res.string.screen_profiles,
                    HermesIcon.AccountCircle,
                    DrawerSection.CONVERSE,
                ) { sessionId, openDrawer -> ProfilesScreenContent(onOpenDrawer = openDrawer) },
                ScreenDefinition(
                    CronJobsScreen,
                    Res.string.screen_cron,
                    HermesIcon.Schedule,
                    DrawerSection.AUTOMATE,
                ) { sessionId, openDrawer -> CronJobsScreenContent(onOpenDrawer = openDrawer) },
                ScreenDefinition(
                    WebhooksScreen,
                    Res.string.screen_webhooks,
                    HermesIcon.Webhook,
                    DrawerSection.AUTOMATE,
                ) { sessionId, openDrawer -> WebhooksScreenContent(onOpenDrawer = openDrawer) },
                ScreenDefinition(
                    KanbanScreen,
                    Res.string.screen_kanban,
                    HermesIcon.Dashboard,
                    DrawerSection.AUTOMATE,
                ) { sessionId, openDrawer -> KanbanScreenContent(onOpenDrawer = openDrawer) },
                ScreenDefinition(
                    SkillsScreen,
                    Res.string.screen_skills,
                    HermesIcon.Extension,
                    DrawerSection.CONFIGURE,
                ) { sessionId, openDrawer -> SkillsScreenContent(onOpenDrawer = openDrawer) },
                ScreenDefinition(
                    ToolsetsScreen,
                    Res.string.screen_toolsets,
                    HermesIcon.Build,
                    DrawerSection.CONFIGURE,
                ) { sessionId, openDrawer -> ToolsetsScreenContent(onOpenDrawer = openDrawer) },
                ScreenDefinition(
                    PluginsScreen,
                    Res.string.screen_plugins,
                    HermesIcon.Memory,
                    DrawerSection.CONFIGURE,
                ) { sessionId, openDrawer -> PluginsScreenContent(onOpenDrawer = openDrawer) },
                ScreenDefinition(
                    ConfigScreen,
                    Res.string.screen_config,
                    HermesIcon.Code,
                    DrawerSection.CONFIGURE,
                ) { sessionId, openDrawer -> ConfigScreenContent(onOpenDrawer = openDrawer) },
                ScreenDefinition(
                    McpServersScreen,
                    Res.string.screen_mcp_servers,
                    HermesIcon.Dashboard,
                    DrawerSection.CONFIGURE,
                ) { sessionId, openDrawer -> McpServersScreenContent(onOpenDrawer = openDrawer) },
                ScreenDefinition(
                    ModelScreen,
                    Res.string.screen_models,
                    HermesIcon.Psychology,
                    DrawerSection.CONFIGURE,
                ) { sessionId, openDrawer -> ModelScreenContent(onOpenDrawer = openDrawer) },
                ScreenDefinition(
                    KeysScreen,
                    Res.string.screen_keys,
                    HermesIcon.Key,
                    DrawerSection.CONFIGURE,
                ) { sessionId, openDrawer -> KeysScreenContent(onOpenDrawer = openDrawer) },
                ScreenDefinition(
                    ChannelsScreen,
                    Res.string.screen_channels,
                    HermesIcon.ListAlt,
                    DrawerSection.CONFIGURE,
                ) { sessionId, openDrawer -> ChannelsScreenContent(onOpenDrawer = openDrawer) },
                ScreenDefinition(
                    ProvidersScreen,
                    Res.string.screen_providers,
                    HermesIcon.Cloud,
                    DrawerSection.CONFIGURE,
                ) { sessionId, openDrawer -> ProvidersScreenContent(onOpenDrawer = openDrawer) },
                ScreenDefinition(
                    GatewayScreen,
                    Res.string.screen_gateway,
                    HermesIcon.Bolt,
                    DrawerSection.INSPECT,
                ) { sessionId, openDrawer -> GatewayScreenContent(onOpenDrawer = openDrawer) },
                ScreenDefinition(
                    SystemScreen,
                    Res.string.screen_system,
                    HermesIcon.Info,
                    DrawerSection.INSPECT,
                ) { sessionId, openDrawer -> SystemScreenContent(onOpenDrawer = openDrawer) },
                ScreenDefinition(
                    LogsScreen,
                    Res.string.screen_logs,
                    HermesIcon.HistoryEdu,
                    DrawerSection.INSPECT,
                ) { sessionId, openDrawer -> LogsScreenContent(onOpenDrawer = openDrawer) },
                ScreenDefinition(
                    ProcessesScreen,
                    Res.string.screen_processes,
                    HermesIcon.Memory,
                    DrawerSection.INSPECT,
                ) { sessionId, openDrawer -> ProcessesScreenContent(onOpenDrawer = openDrawer) },
                ScreenDefinition(
                    AnalyticsScreen,
                    Res.string.screen_analytics,
                    HermesIcon.BarChart,
                    DrawerSection.INSPECT,
                ) { sessionId, openDrawer -> AnalyticsScreenContent(onOpenDrawer = openDrawer) },
                ScreenDefinition(
                    BillingScreen,
                    Res.string.screen_billing,
                    HermesIcon.AccountBalanceWallet,
                    DrawerSection.INSPECT,
                ) { sessionId, openDrawer -> BillingScreenContent(onOpenDrawer = openDrawer) },
                ScreenDefinition(
                    FilesScreen,
                    Res.string.screen_files,
                    HermesIcon.Folder,
                    DrawerSection.INSPECT,
                ) { sessionId, openDrawer -> FilesScreenContent(onOpenDrawer = openDrawer) },
                ScreenDefinition(
                    AchievementsScreen,
                    Res.string.screen_achievements,
                    HermesIcon.EmojiEvents,
                    DrawerSection.INSPECT,
                ) { sessionId, openDrawer -> AchievementsScreenContent(onOpenDrawer = openDrawer) },
                ScreenDefinition(
                    SettingsScreen,
                    Res.string.screen_settings,
                    HermesIcon.Settings,
                    DrawerSection.INSPECT,
                ) { sessionId, openDrawer ->
                    SettingsScreenContent(
                        onOpenDrawer = openDrawer,
                        onNavigateToLogin = {
                            NavigationController.navigateTo(AuthLoginScreen)
                        },
                    )
                },
            ),
        primary =
            setOf(
                ChatScreen,
                BotsScreen,
                SkillsScreen,
                CronJobsScreen,
                SystemScreen,
                SettingsScreen,
            ),
    )
