package com.m57.hermescontrol.ui.mcp

/**
 * True when [raw] is an ordinary HTTPS URL: `https` scheme, a non-blank host,
 * and no credentials embedded in the authority.
 *
 * The URL arrives from a remote dashboard response and is handed to another app
 * through an Android intent, so this fails closed on malformed input, custom
 * schemes and `user:pass@host` authorities.
 *
 * Platform split: Android/JVM parse with `java.net.URI`; wasmJs uses the
 * browser's own `URL` parser. Both reject anything the other would.
 */
expect fun isSafeHttpsAuthorizationUrl(raw: String): Boolean
