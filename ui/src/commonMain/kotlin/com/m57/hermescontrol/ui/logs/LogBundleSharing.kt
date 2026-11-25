package com.m57.hermescontrol.ui.logs

import androidx.compose.runtime.Composable

/**
 * Builds the "share the exported log bundle" action for the current composition.
 *
 * What happens is a platform decision: Android copies to the clipboard and opens
 * the ACTION_SEND chooser, desktop copies to the system clipboard, and the
 * browser copies through the async clipboard API. A function is returned rather
 * than exposing the platform handle, so callers stay platform-neutral.
 */
@Composable
expect fun rememberLogSharer(): (String) -> Boolean
