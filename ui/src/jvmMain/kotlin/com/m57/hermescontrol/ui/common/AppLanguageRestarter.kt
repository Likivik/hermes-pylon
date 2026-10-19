package com.m57.hermescontrol.ui.common

import androidx.compose.runtime.Composable

/** Desktop has no Activity to recreate; the language preference is read on demand. */
@Composable
actual fun rememberAppLanguageRestarter(): () -> Unit = {}
