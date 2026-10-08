package com.m57.hermescontrol.data.remote

import com.m57.hermescontrol.BuildConfig
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Android-BuildConfig-aware [ServerEndpoint.parse] wrapper. Lives in :app
 * because the build-flag is generated per-build and the `:shared` module
 * has no Android `BuildConfig`.
 */
fun ServerEndpoint.Companion.parseForBuild(rawValue: String): ServerEndpoint =
    parse(
        rawValue,
        if (BuildConfig.ALLOW_CLEARTEXT) {
            CleartextPolicy.ALLOW_WITH_WARNING
        } else {
            CleartextPolicy.DENY
        },
    )

/**
 * `ServerEndpoint.resolve(relativePath)` extension shim for the :app
 * side. The pre-Phase-3 `ServerEndpoint` exposed
 * `fun resolve(relativePath: String): okhttp3.HttpUrl`, which :app
 * call sites and the existing test suite (notably
 * `AuthenticatedDisposableFixtureTest` and the round-trip checks in
 * `ServerEndpointTest`) still depend on.
 *
 * The platform-neutral resolve that lives in :shared is
 * [ServerBaseUrl.resolve]; it returns the multiplatform [ServerBaseUrl]
 * which doesn't fit OkHttp's `Request.Builder.url(...)` signature. So we
 * bridge here: take the canonical URL string the shared wrapper
 * produces, parse it through OkHttp, and hand back a `HttpUrl` whose
 * `.newBuilder().addQueryParameter(...).build()` chain still works for
 * the test fixture (issue: AuthenticatedDisposableFixtureTest uses
 * exactly that pattern on the resolved URL).
 */
fun ServerEndpoint.resolve(relativePath: String): HttpUrl =
    baseUrl.resolve(relativePath).toString().toHttpUrlOrNull()
        ?: error("Cannot convert resolved URL to HttpUrl: $relativePath")
