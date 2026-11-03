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
