package com.m57.hermescontrol.ui.chat

/**
 * Per-session rail metadata (icon emoji). A plain model so the rail composable
 * never depends on ChatViewModel — keeps SessionRail pure/previewable.
 */
data class RailMeta(val icon: String? = null)
