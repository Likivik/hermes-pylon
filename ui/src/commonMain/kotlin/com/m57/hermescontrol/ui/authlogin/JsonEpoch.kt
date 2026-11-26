package com.m57.hermescontrol.ui.authlogin

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * An epoch-seconds field from a JSON body: the number when it looks like one, the number written
 * as a string otherwise.
 *
 * Two rules, both learned the hard way:
 *
 * - Anything unreadable is `null`, not an error. [NativeToken.needsRefresh] reads unknown as "do
 *   not refresh pre-emptively", which is the safe direction: a wrong guess costs one request, not
 *   a dead session.
 * - A **non-positive** value is a sentinel, not a date. The gateway sends `0` for a session with
 *   no expiry of its own; read literally that is 1970, so the token would look permanently expired
 *   and every call would refresh — each refresh rotating the token, which is how a working session
 *   becomes a dead one.
 *
 * There is one copy of this on purpose. It lives here, as a top-level function, because both the
 * network boundary ([NativeTokenExchange]) and the storage boundary ([com.m57.hermescontrol.data.local.NativeTokenStore])
 * parse the same field; two copies is how a fix reaches one path and misses the other.
 */
internal fun JsonObject.epochSecondsOrNull(key: String): Long? {
    val primitive = this[key] as? JsonPrimitive ?: return null
    val seconds = primitive.longOrNull ?: primitive.takeIf { it.isString }?.content?.toLongOrNull()
    return seconds?.takeIf { it > 0 }
}
