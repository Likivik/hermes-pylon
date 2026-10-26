package com.m57.hermescontrol.ui.diagnostics

import android.os.Build

actual fun diagnosticsEnvironment(): DiagnosticsEnvironment =
    DiagnosticsEnvironment(
        target = "Android ${Build.VERSION.SDK_INT}",
        origin = null,
    )
