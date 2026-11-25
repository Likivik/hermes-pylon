package com.m57.hermescontrol.ui.authlogin

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Sign out is a page navigation on the web. Which page runs decides whether the
 * server session is actually destroyed, so the choice is pinned here.
 */
class SignOutPageTest {
    @Test
    fun userSignOutDestroysTheSessionBeforeLeaving() {
        assertEquals("logout.html", signOutPageFor(SignOutReason.USER_INITIATED))
    }

    @Test
    fun expiredSessionGoesStraightToTheForm() {
        assertEquals("login.html", signOutPageFor(SignOutReason.SESSION_EXPIRED))
    }
}
