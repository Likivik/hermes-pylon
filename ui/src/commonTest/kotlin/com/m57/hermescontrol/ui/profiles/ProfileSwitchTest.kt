package com.m57.hermescontrol.ui.profiles

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The gateway's `active` is a preference; `current` is what its process serves. They do not
 * follow each other, so only agreement may be reported as a switch.
 */
class ProfileSwitchTest {
    @Test
    fun matchingProfileIsActivated() {
        assertEquals(ProfileSwitch.Activated("work"), profileSwitchOutcome("work", "work"))
    }

    @Test
    fun divergentProfileStaysPending() {
        val outcome = profileSwitchOutcome("work", "default")
        assertTrue(outcome is ProfileSwitch.Pending, outcome.toString())
        assertEquals("work", outcome.selected)
        assertEquals("default", outcome.serving)
    }

    @Test
    fun unknownServingProfileIsNeverSuccess() {
        val outcome = profileSwitchOutcome("work", null)
        assertTrue(outcome is ProfileSwitch.Pending, outcome.toString())
        assertEquals(null, outcome.serving)
    }
}
