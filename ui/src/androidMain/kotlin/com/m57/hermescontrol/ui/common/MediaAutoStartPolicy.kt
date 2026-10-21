package com.m57.hermescontrol.ui.common

import androidx.lifecycle.Lifecycle

/**
 * A play request only auto-starts when the screen is at least STARTED.
 *
 * Media that begins buffering behind a stopped activity is a wasted download and
 * a user surprise; this is the policy the player actual applies on `onPrepared`.
 */
fun shouldAutoStartMedia(state: Lifecycle.State): Boolean = state.isAtLeast(Lifecycle.State.STARTED)
