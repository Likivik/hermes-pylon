package com.m57.hermescontrol.ui.common

import androidx.compose.runtime.Composable

/**
 * Shows a one-shot transient message for [toastMessage], then calls [onClearToast].
 *
 * Each target renders it natively: Android uses `android.widget.Toast`; desktop and
 * web have no toast surface, so their actuals consume the message without drawing
 * (the screens' in-line error state remains the durable signal).
 */
@Composable
expect fun ToastEffect(
    toastMessage: String?,
    onClearToast: () -> Unit,
)
