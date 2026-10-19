package com.m57.hermescontrol.data.remote

import com.m57.hermescontrol.BuildFlags

/**
 * [ServerEndpoint.parse] with the build's cleartext policy applied.
 *
 * Lives here rather than next to `BuildConfig` so every target can resolve a
 * user-entered base URL the same way; [BuildFlags.allowCleartext] carries the
 * per-build flag.
 */
fun ServerEndpoint.Companion.parseForBuild(rawValue: String): ServerEndpoint =
    parse(
        rawValue,
        if (BuildFlags.allowCleartext) {
            CleartextPolicy.ALLOW_WITH_WARNING
        } else {
            CleartextPolicy.DENY
        },
    )
