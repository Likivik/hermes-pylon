package com.m57.hermescontrol.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * Desktop and web have no toast surface; the message is consumed immediately so
 * state never sticks, and screens keep their inline error text as the signal.
 */
@Composable
actual fun ToastEffect(
    toastMessage: String?,
    onClearToast: () -> Unit,
) {
    LaunchedEffect(toastMessage) {
        if (toastMessage != null) onClearToast()
    }
}
