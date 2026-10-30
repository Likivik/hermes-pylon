package com.m57.hermescontrol.ui.logs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@JsFun("(text) => { try { navigator.clipboard.writeText(text); return true } catch (e) { return false } }")
private external fun jsWriteClipboard(text: String): Boolean

@JsFun("() => typeof navigator !== 'undefined' && !!navigator.clipboard")
private external fun jsClipboardAvailable(): Boolean

/** The browser copies to the clipboard; there is no share sheet here. */
@Composable
actual fun rememberLogSharer(): (String) -> Boolean =
    remember {
        { text -> jsClipboardAvailable() && runCatching { jsWriteClipboard(text) }.getOrDefault(false) }
    }
