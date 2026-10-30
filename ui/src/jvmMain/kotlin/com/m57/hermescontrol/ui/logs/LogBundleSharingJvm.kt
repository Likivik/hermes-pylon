package com.m57.hermescontrol.ui.logs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/** Desktop has no share sheet: copy to the system clipboard. */
@Composable
actual fun rememberLogSharer(): (String) -> Boolean =
    remember {
        { text ->
            runCatching {
                Toolkit
                    .getDefaultToolkit()
                    .systemClipboard
                    .setContents(StringSelection(text), null)
            }.isSuccess
        }
    }
