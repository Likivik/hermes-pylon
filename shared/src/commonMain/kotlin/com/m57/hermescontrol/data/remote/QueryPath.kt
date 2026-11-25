package com.m57.hermescontrol.data.remote

import io.ktor.http.encodeURLParameter

/**
 * A route plus query parameters, escaped by Ktor's encoder instead of ours.
 *
 * This replaced a private hand-rolled encoder: a UTF-8 byte loop, a hex table, and a comment explaining it was
 * kept local "so the encoding cannot drift with a Ktor helper's defaults". The reasoning was backwards — a
 * hand-rolled encoder is the thing that drifts, and its only test was however the app happened to be used.
 *
 * Two Ktor shapes were tried and rejected on evidence, not preference:
 *
 * - `URLBuilder(path).buildString()` turns the relative route `api/logs` into `http://localhost/api/logs`. A
 *   relative route must stay relative: the client joins it to the gateway's base URL, and an absolute string
 *   would silently point somewhere else.
 * - Its query writing turns a space into `+`. That is form encoding; a bare `+` in a query value reads back as a
 *   space, so a value that really contains `+` could never be sent correctly.
 *
 * `encodeURLParameter(spaceToPlus = false)` reproduces the old byte-level output. Measured, not assumed:
 * `a b+c/d` becomes `a%20b%2Bc%2Fd`, where `encodeURLQueryComponent` leaves `+` and `/` bare — a value with a
 * plus or a slash would arrive as different data.
 *
 * It lives at top level so a test can call it: this builds every GET route in the app, which is not a detail
 * worth hiding from tests.
 */
internal fun queryPath(
    path: String,
    vararg params: Pair<String, String?>,
): String {
    val query =
        params.mapNotNull { (name, value) ->
            value?.let { "$name=" + it.encodeURLParameter(spaceToPlus = false) }
        }
    return if (query.isEmpty()) path else "$path?" + query.joinToString("&")
}
