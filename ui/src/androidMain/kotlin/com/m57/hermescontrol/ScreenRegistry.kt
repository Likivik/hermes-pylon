package com.m57.hermescontrol

import com.m57.hermescontrol.generated.resources.Res
import com.m57.hermescontrol.generated.resources.screen_processes
import com.m57.hermescontrol.ui.icons.HermesIcon
import com.m57.hermescontrol.ui.process.ProcessesScreen as ProcessesScreenContent

/**
 * The one screen whose content is Android-only today: process inspection reads
 * the platform's process table. Every other screen lives in `ScreenGraphEntries.kt`.
 */
actual fun platformScreens(): List<ScreenDefinition> =
    listOf(
        ScreenDefinition(
            ProcessesScreen,
            Res.string.screen_processes,
            HermesIcon.Memory,
            DrawerSection.INSPECT,
        ) { _, openDrawer -> ProcessesScreenContent(onOpenDrawer = openDrawer) },
    )
