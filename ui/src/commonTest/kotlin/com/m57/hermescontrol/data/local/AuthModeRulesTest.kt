package com.m57.hermescontrol.data.local

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * "Token is required" was reported on a connection that worked: a cookie session has
 * no token, and the screens asked for one anyway. The rule below is the whole bug.
 */
class AuthModeRulesTest {
    @Test
    fun cookieSessionNeedsNoToken() {
        assertFalse(missingRequiredToken(token = "", isGatedMode = true))
    }

    @Test
    fun tokenAndBasicModesStillNeedAToken() {
        assertTrue(missingRequiredToken(token = "", isGatedMode = false))
        assertTrue(missingRequiredToken(token = "   ", isGatedMode = false))
    }

    @Test
    fun aTokenSatisfiesEitherMode() {
        assertFalse(missingRequiredToken(token = "abc", isGatedMode = false))
        assertFalse(missingRequiredToken(token = "abc", isGatedMode = true))
    }
}
