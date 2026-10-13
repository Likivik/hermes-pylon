package com.m57.hermescontrol.data.remote

import kotlinx.io.IOException

/**
 * wasmJs: the browser's `fetch` gives one opaque failure for every transport
 * problem — DNS, refused connection, TLS, CORS are not distinguishable from
 * script, so the JVM taxonomy has nothing to match on. A retry is always worth
 * attempting: the common case is a flaky network, and the callers bound retries.
 */
actual fun isRetryable(e: IOException): Boolean = true
