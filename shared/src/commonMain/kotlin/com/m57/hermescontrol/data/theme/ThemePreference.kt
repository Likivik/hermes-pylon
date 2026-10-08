// Split out of :app's theme/Theme.kt so the persistence layer can live in
// :shared's commonMain without depending on the Compose color schemes.

package com.m57.hermescontrol.data.theme

import kotlinx.serialization.Serializable

/** Light / dark / follow-system preference. The Compose color-scheme logic
 *  that consumes this enum still lives in :app. */
@Serializable
enum class ThemePreference { SYSTEM, LIGHT, DARK }
