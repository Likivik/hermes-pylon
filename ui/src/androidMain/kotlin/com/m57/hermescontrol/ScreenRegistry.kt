package com.m57.hermescontrol

/**
 * Screens only this target can render.
 *
 * Empty on every target today. The last entry was the Processes screen, which
 * was assumed to need the platform process table; it does not — it renders the
 * gateway process rows through `ProcessesViewModel`, which has always lived in
 * `commonMain`. It now ships from `commonScreens()` like every other screen.
 *
 * The seam stays: a screen that genuinely needs a platform API (a file picker,
 * a camera, a notification channel) belongs here, not in the shared list.
 */
actual fun platformScreens(): List<ScreenDefinition> = emptyList()
