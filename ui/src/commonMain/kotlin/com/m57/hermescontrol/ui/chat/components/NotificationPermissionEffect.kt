package com.m57.hermescontrol.ui.chat.components

import androidx.compose.runtime.Composable

/**
 * Asks for the platform notification permission the first time chat opens.
 *
 * Android needs an explicit POST_NOTIFICATIONS grant from API 33; desktop has no
 * such concept and the web asks its own way, so those targets do nothing.
 */
@Composable
expect fun RequestNotificationPermissionOnStart()
