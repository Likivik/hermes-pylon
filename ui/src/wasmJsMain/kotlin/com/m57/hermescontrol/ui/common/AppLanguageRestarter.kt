package com.m57.hermescontrol.ui.common

import androidx.compose.runtime.Composable

/** The web host has no Activity to recreate; a reload is a user action, not ours. */
@Composable
actual fun rememberAppLanguageRestarter(): () -> Unit = {}
