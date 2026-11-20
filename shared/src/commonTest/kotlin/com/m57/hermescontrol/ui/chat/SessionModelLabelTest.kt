package com.m57.hermescontrol.ui.chat

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The provider-qualified label rule, tested once.
 *
 * Both call sites used to inline this, so a change to one would have left the composer chip and the
 * model picker disagreeing with no test failing.
 */
class SessionModelLabelTest {
    @Test
    fun anUnqualifiedModelGetsTheProviderPrefix() {
        assertEquals("openai/gpt-5", qualifyModelLabel("openai", "gpt-5"))
    }

    @Test
    fun anAlreadyQualifiedModelIsLeftAlone() {
        assertEquals("openai/gpt-5", qualifyModelLabel("openai", "openai/gpt-5"))
    }

    @Test
    fun anUnknownProviderLeavesTheModelAsIs() {
        assertEquals("gpt-5", qualifyModelLabel("", "gpt-5"))
    }

    @Test
    fun aModelLabelledWithAnotherProviderAlsoGetsPrefixed() {
        // Characterisation, not endorsement: the rule only checks the REPORTED provider, so an
        // inconsistent payload produces "openai/anthropic/claude". Pinned so a future fix to the
        // rule is a deliberate, visible change rather than a silent drift.
        assertEquals("openai/anthropic/claude", qualifyModelLabel("openai", "anthropic/claude"))
    }
}
