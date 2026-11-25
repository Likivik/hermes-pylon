package com.m57.hermescontrol.data.local

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Disk-backed on Android, keyed per profile — the same store the app has always used. */
@Composable
actual fun rememberAnalyticsCacheStore(): AnalyticsCacheStore {
    val context = LocalContext.current.applicationContext
    return remember(context) { AndroidAnalyticsCacheStore(context) }
}
