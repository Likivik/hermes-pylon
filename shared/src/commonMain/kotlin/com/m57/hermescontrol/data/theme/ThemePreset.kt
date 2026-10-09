// Split out of :app's theme/Theme.kt so the persistence layer can live in
// :shared's commonMain without depending on the Compose color schemes.

package com.m57.hermescontrol.data.theme

import kotlinx.serialization.Serializable

/** Named palette preset. The Compose color schemes that realise each preset
 *  still live in :app. */
@Serializable
enum class ThemePreset { DEFAULT, MONOCHROME, GRUVBOX, CATPPUCCIN, AMOLED, NEON_NOIR }
