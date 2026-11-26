package com.m57.hermescontrol.ui.authlogin

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * One parser, two call sites — the network boundary and the storage boundary.
 *
 * The 0 case is here because it was the bug: a session with no expiry of its own arrives as `0`,
 * and read literally that is 1970, so every call would refresh and every refresh would rotate the
 * token. It has to keep meaning "unknown" on both paths, which is why there is one copy of this.
 */
class JsonEpochTest {
    private fun parse(json: String): Long? = Json.parseToJsonElement(json).jsonObject.epochSecondsOrNull("expires_at")

    @Test
    fun aNumberIsReadAsSeconds() {
        assertEquals(1_893_456_000L, parse("""{"expires_at":1893456000}"""))
    }

    @Test
    fun aNumberWrittenAsAStringIsReadToo() {
        assertEquals(1_893_456_000L, parse("""{"expires_at":"1893456000"}"""))
    }

    @Test
    fun zeroIsASentinelNot1970() {
        assertNull(parse("""{"expires_at":0}"""))
    }

    @Test
    fun zeroWrittenAsAStringIsAlsoASentinel() {
        assertNull(parse("""{"expires_at":"0"}"""))
    }

    @Test
    fun aNegativeValueIsNotADate() {
        assertNull(parse("""{"expires_at":-1}"""))
    }

    @Test
    fun aMissingFieldIsUnknown() {
        assertNull(parse("""{"access_token":"a"}"""))
    }

    @Test
    fun anUnreadableFieldIsUnknownNotAnError() {
        assertNull(parse("""{"expires_at":{"weird":true}}"""))
        assertNull(parse("""{"expires_at":"not-a-time"}"""))
    }
}
