package com.m57.hermescontrol.ui.keys

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberSensitiveCopy(): (label: String, text: String) -> Unit {
    val context = LocalContext.current
    return { label, text -> copySensitiveText(context = context, label = label, text = text) }
}
