package com.m57.hermescontrol.data.local

/**
 * A cookie session has no token by design — the browser holds it — so "no token"
 * must only mean "not configured" for token and basic modes.
 *
 * The screens used to test `token.isBlank()` alone, which reported "Token is
 * required" on a connection that was working perfectly well.
 */
internal fun missingRequiredToken(
    token: String,
    isGatedMode: Boolean,
): Boolean = !isGatedMode && token.isBlank()
