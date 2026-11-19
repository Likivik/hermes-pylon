package com.m57.hermescontrol.ui.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SessionPreferenceKeysTest {
    @Test
    fun eachProfileKeepsItsOwnLastSession() {
        assertNotEquals(
            SessionPreferenceKeys.lastSession("work"),
            SessionPreferenceKeys.lastSession("default"),
        )
    }

    @Test
    fun noSelectionUsesTheLaunchProfileSlot() {
        assertEquals(SessionPreferenceKeys.lastSession(null), SessionPreferenceKeys.lastSession("default"))
    }

    @Test
    fun aBlankSelectionIsTreatedAsAbsent() {
        assertEquals(SessionPreferenceKeys.lastSession("  "), SessionPreferenceKeys.lastSession(null))
    }

    @Test
    fun theLegacyKeyIsNeverHandedOut() {
        // The old global key cannot be attributed to a profile, so it is not migrated.
        listOf(null, "work", "default").forEach {
            assertNotEquals(SessionPreferenceKeys.LEGACY_LAST_SESSION, SessionPreferenceKeys.lastSession(it))
        }
    }
}
