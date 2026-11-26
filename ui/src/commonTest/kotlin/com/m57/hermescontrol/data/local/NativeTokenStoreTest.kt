package com.m57.hermescontrol.data.local

import com.m57.hermescontrol.data.security.AUTH_SECRET_PREFIX
import com.m57.hermescontrol.data.security.SecretStore
import com.m57.hermescontrol.ui.authlogin.NativeToken
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NativeTokenStoreTest {
    private val secrets = FakeSecretStore()
    private val store = NativeTokenStore(secrets)

    private val token =
        NativeToken(
            accessToken = "at-1",
            refreshToken = "rt-1",
            expiresAtEpochSeconds = 1_893_456_000L,
            provider = "nous",
        )

    @Test
    fun roundTripsEveryField() {
        store.save(token)

        assertEquals(token, store.load())
    }

    /** An unknown expiry must stay unknown. Coerced to 0 it would read as "expired long ago". */
    @Test
    fun anUnknownExpirySurvivesTheRoundTripAsUnknown() {
        store.save(token.copy(expiresAtEpochSeconds = null))

        assertNull(assertNotNull(store.load()).expiresAtEpochSeconds)
    }

    @Test
    fun clearRemovesTheToken() {
        store.save(token)

        store.clear()

        assertNull(store.load())
    }

    /**
     * One write per save. Splitting the pair across four keys would allow a new access token to
     * sit beside an old refresh token, which fails on the *next* refresh instead of here.
     */
    @Test
    fun saveWritesExactlyOneEntry() {
        store.save(token)

        assertEquals(1, secrets.values.size)
    }

    @Test
    fun theEntryIsNamespacedUnderTheAuthPrefix() {
        store.save(token)

        assertEquals(true, secrets.values.keys.single().startsWith(AUTH_SECRET_PREFIX))
    }

    @Test
    fun anUnreadableValueReadsAsAbsentRatherThanThrowing() {
        secrets.values["auth/native/token"] = "{not json"

        assertNull(store.load())
    }

    @Test
    fun aValueMissingTheRefreshTokenReadsAsAbsent() {
        secrets.values["auth/native/token"] = """{"access_token":"a"}"""

        assertNull(store.load())
    }

    @Test
    fun savingTwiceReplacesRatherThanAppends() {
        store.save(token)
        store.save(token.copy(accessToken = "at-2"))

        assertEquals("at-2", assertNotNull(store.load()).accessToken)
    }

    private class FakeSecretStore : SecretStore {
        val values = mutableMapOf<String, String>()

        override fun getString(logicalKey: String): String? = values[logicalKey]

        override fun putString(
            logicalKey: String,
            value: String?,
        ) {
            if (value == null) values.remove(logicalKey) else values[logicalKey] = value
        }

        override fun deletePrefix(prefix: String) {
            values.keys.filter { it.startsWith(prefix) }.forEach(values::remove)
        }
    }
}
