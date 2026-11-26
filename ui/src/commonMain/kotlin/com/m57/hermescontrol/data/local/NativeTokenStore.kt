package com.m57.hermescontrol.data.local

import com.m57.hermescontrol.data.security.SecretStore
import com.m57.hermescontrol.data.security.authSecretKey
import com.m57.hermescontrol.ui.authlogin.NativeToken
import com.m57.hermescontrol.ui.authlogin.epochSecondsOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Persists the native sign-in token pair in the device's [SecretStore].
 *
 * One key, not four: a partially written set (new access token, old refresh token) is an
 * unrecoverable state that only shows up on the next refresh, far from the write that caused it.
 * A single `putString` either lands or it does not.
 *
 * There is no per-platform code here on purpose — [SecretStore] is already Keystore-backed on
 * Android, so a second storage seam would be a second thing to get wrong.
 */
class NativeTokenStore(
    private val secrets: SecretStore,
) {
    fun load(): NativeToken? {
        val raw = secrets.getString(KEY) ?: return null
        return decode(raw)
    }

    fun save(token: NativeToken) {
        secrets.putString(KEY, encode(token))
    }

    fun clear() {
        secrets.putString(KEY, null)
    }

    /**
     * A value that cannot be read is treated as absent rather than as an error: the user signs in
     * again, which is the same outcome as an expired token and never a crash on launch.
     */
    private fun decode(raw: String): NativeToken? {
        val body =
            try {
                Json.parseToJsonElement(raw).jsonObject
            } catch (_: Exception) {
                return null
            }

        val access = body.stringOrNull("access_token")
        val refresh = body.stringOrNull("refresh_token")
        if (access.isNullOrEmpty() || refresh.isNullOrEmpty()) return null

        return NativeToken(
            accessToken = access,
            refreshToken = refresh,
            // Absent stays absent: coercing it to 0 would read as "expired long ago".
            expiresAtEpochSeconds = body.epochSecondsOrNull("expires_at"),
            provider = body.stringOrNull("provider").orEmpty(),
        )
    }

    private fun encode(token: NativeToken): String =
        buildJsonObject {
            put("access_token", token.accessToken)
            put("refresh_token", token.refreshToken)
            token.expiresAtEpochSeconds?.let { put("expires_at", it) }
            put("provider", token.provider)
        }.toString()

    private fun JsonObject.stringOrNull(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private companion object {
        val KEY = authSecretKey("native/token")
    }
}
