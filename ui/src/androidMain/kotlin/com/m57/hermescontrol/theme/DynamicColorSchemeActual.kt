package com.m57.hermescontrol.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Android actual for [dynamicColorSchemeOrNull].
 *
 * Material You dynamic colour is available on Android 12+ (API 31 / `S`).
 * On older devices the actual returns `null` so the theme falls back to
 * the preset's resolved scheme.
 *
 * Uses `LocalContext.current` to satisfy `dynamic*ColorScheme`'s
 * `Context` parameter; the context is provided by the Compose tree, not
 * captured at construction time.
 */
@Composable
actual fun dynamicColorSchemeOrNull(darkTheme: Boolean): ColorScheme? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return null
    }
    val context = LocalContext.current
    return if (darkTheme) {
        dynamicDarkColorScheme(context)
    } else {
        dynamicLightColorScheme(context)
    }
}
