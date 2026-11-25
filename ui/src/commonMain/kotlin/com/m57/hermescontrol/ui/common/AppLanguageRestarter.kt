package com.m57.hermescontrol.ui.common

import androidx.compose.runtime.Composable

/**
 * Returns a callback that re-creates the host UI so a newly selected app language
 * takes effect.
 *
 * Android recreates the Activity (the locale is applied in `attachBaseContext`);
 * desktop and web return a no-op — the language preference still applies, it just
 * does not need a host restart to be read.
 *
 * Contract: the returned lambda is always callable. A no-op means the platform reads the
 * new language on the next recomposition (web, desktop); it does **not** mean the setting
 * was ignored. Only Android needs the host restart.
 */
@Composable
expect fun rememberAppLanguageRestarter(): () -> Unit
