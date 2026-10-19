package com.m57.hermescontrol.ui.keys

import androidx.compose.runtime.Composable

/**
 * Returns a copier for secret values.
 *
 * Android marks the clip as sensitive (`ClipDescription.EXTRA_IS_SENSITIVE`) so it
 * stays out of the clipboard preview and the recents screenshot; other platforms
 * have no equivalent flag, and their actuals copy plainly. The `Context` stops at
 * the actual, which is why this is a composable-scoped supplier rather than a plain
 * function taking a context the shared screen cannot name.
 */
@Composable
expect fun rememberSensitiveCopy(): (label: String, text: String) -> Unit
