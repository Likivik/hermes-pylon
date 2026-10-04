package com.m57.hermescontrol.ui.settings.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportedLanguagesTest {
    @Test
    fun arabicIsSupportedWithoutDroppingExistingLanguages() {
        assertEquals(listOf("system", "en", "ar", "zh", "ja", "ko"), SUPPORTED_LANGUAGE_CODES)
        assertEquals(SUPPORTED_LANGUAGE_CODES.size, SUPPORTED_LANGUAGE_CODES.toSet().size)
        assertTrue("ar" in SUPPORTED_LANGUAGE_CODES)
    }
}
