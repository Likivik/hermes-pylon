package com.m57.hermescontrol.ui.authlogin

/**
 * Nothing to do and nothing taken over: the store is the session on these
 * targets, so the clear that precedes this call is the logout and the navigation
 * layer already reacts to the latch.
 */
internal actual fun handleSignOut(reason: SignOutReason): Boolean = false
