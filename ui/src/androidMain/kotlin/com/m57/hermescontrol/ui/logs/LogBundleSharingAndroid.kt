package com.m57.hermescontrol.ui.logs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Android copies to the clipboard and opens the share chooser — the same
 * behaviour the logs screen had while it lived in the Android source set.
 */
@Composable
actual fun rememberLogSharer(): (String) -> Boolean {
    val context = LocalContext.current
    return remember(context) {
        { text ->
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText("Hermes Logs", text))
            try {
                val sendIntent =
                    Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, text)
                        type = "text/plain"
                    }
                context.startActivity(Intent.createChooser(sendIntent, "Share logs"))
                true
            } catch (_: Exception) {
                clipboard != null
            }
        }
    }
}
