package com.m57.hermescontrol.ui.keys

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString

@Composable
actual fun rememberSensitiveCopy(): (label: String, text: String) -> Unit {
    val clipboard = LocalClipboardManager.current
    return { _, text -> clipboard.setText(AnnotatedString(text)) }
}
