package com.m57.hermescontrol.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext

/** Android renders the message as a real `android.widget.Toast`. */
@Composable
actual fun ToastEffect(
    toastMessage: String?,
    onClearToast: () -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(toastMessage) {
        toastMessage?.let { msg ->
            android.widget.Toast
                .makeText(context, msg, android.widget.Toast.LENGTH_SHORT)
                .show()
            onClearToast()
        }
    }
}
