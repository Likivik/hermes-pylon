package com.m57.hermescontrol.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/**
 * JVM actual for [dynamicColorSchemeOrNull].
 *
 * Material You dynamic colour is an Android-only concept; on the JVM
 * (desktop via Skiko) we have no wallpaper-derived palette, so the
 * actual always returns `null`. The theme falls back to the preset's
 * resolved scheme.
 */
@Composable
actual fun dynamicColorSchemeOrNull(darkTheme: Boolean): ColorScheme? = null
