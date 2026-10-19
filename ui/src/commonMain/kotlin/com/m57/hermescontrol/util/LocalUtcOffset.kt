package com.m57.hermescontrol.util

/**
 * The device's current offset from UTC, in minutes (Moscow = +180).
 *
 * Needed because the shared date formatter does plain arithmetic: it parses a UTC
 * timestamp and has no calendar library to convert with. Without this the UI would
 * silently show UTC instead of the user's local time.
 */
expect fun localUtcOffsetMinutes(): Int
