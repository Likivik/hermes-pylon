@file:Suppress("ktlint:standard:filename")

package com.m57.hermescontrol.ui.common

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable

/** Android applies the locale in `attachBaseContext`, so the Activity is recreated. */
@Composable
actual fun rememberAppLanguageRestarter(): () -> Unit {
    val activity = LocalActivity.current
    return { activity?.recreate() }
}
