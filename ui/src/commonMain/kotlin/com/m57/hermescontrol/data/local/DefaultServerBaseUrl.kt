package com.m57.hermescontrol.data.local

/**
 * The gateway URL a fresh install should suggest.
 *
 * On Android and desktop that is loopback. In a browser it cannot be: a page
 * served from one origin cannot fetch another, and the failure is not a
 * degraded response but a refused request ("Failed to fetch"), so the default
 * has to be the origin the page was loaded from.
 */
expect val defaultServerBaseUrl: String

/**
 * Whether the gateway URL is *fixed* by where the page was loaded from.
 *
 * True in a browser: the page can only talk to its own origin, so a stored or
 * typed URL pointing anywhere else is not a preference, it is a guaranteed
 * "Failed to fetch". Android and desktop can reach any host, so they stay
 * editable.
 */
expect val serverBaseUrlIsPageOrigin: Boolean
