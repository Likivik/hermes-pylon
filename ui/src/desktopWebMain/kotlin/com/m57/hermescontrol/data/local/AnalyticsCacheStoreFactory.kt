package com.m57.hermescontrol.data.local

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * desktop has no persistent store yet: the cache lives for the lifetime of the
 * process. Losing it on reload costs one slow `/api/analytics/usage` call, which
 * is the honest behaviour rather than pretending a disk cache exists.
 */
@Composable
actual fun rememberAnalyticsCacheStore(): AnalyticsCacheStore = remember { InMemoryAnalyticsCacheStore() }
