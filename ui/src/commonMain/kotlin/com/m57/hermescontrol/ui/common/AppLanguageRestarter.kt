package com.m57.hermescontrol.ui.common

import androidx.compose.runtime.Composable

/**
 * Returns a callback that re-creates the host UI so a newly selected app language
 * takes effect.
 *
 * Android recreates the Activity (the locale is applied in `attachBaseContext`);
 * desktop and web return a no-op — the language preference still applies, it just
 * does not need a host restart to be read.
 */
@Composable
expect fun rememberAppLanguageRestarter(): () -> Unit
