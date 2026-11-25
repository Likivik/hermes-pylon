package com.m57.hermescontrol.ui.chat

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A rename dialog sends title and icon together, so "no title" has to be told apart from "rename to nothing".
 */
class RenamePlanTest {
    @Test
    fun aRealTitleIsTrimmed() {
        assertEquals(RenamePlan.Title("Deploy notes"), planRename("  Deploy notes  "))
    }

    @Test
    fun aBlankTitleIsAnIconOnlyEdit() {
        assertEquals(RenamePlan.IconOnly, planRename("   "))
    }

    @Test
    fun anEmptyTitleIsAnIconOnlyEdit() {
        assertEquals(RenamePlan.IconOnly, planRename(""))
    }

    @Test
    fun aMissingTitleIsAnIconOnlyEdit() {
        assertEquals(RenamePlan.IconOnly, planRename(null))
    }

    @Test
    fun innerWhitespaceIsKept() {
        assertEquals(RenamePlan.Title("two words"), planRename(" two words "))
    }
}
