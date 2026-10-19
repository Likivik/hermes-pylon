package com.m57.hermescontrol.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.runComposeUiTest
import com.m57.hermescontrol.data.theme.ThemePreference
import com.m57.hermescontrol.theme.HermesControlTheme
import com.m57.hermescontrol.ui.landing.LandingScreen
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Proves the migrated theme + landing surface render off Android.
 *
 * This is the machine check that backs the "theme and LandingScreen now live in
 * commonMain" claim: it composes the real [HermesControlTheme] around the real
 * [LandingScreen] on the JVM (Skiko headless) and asserts the screen produced an
 * interactive control. Before the move this test could not exist — both types
 * were Android-only.
 */
@OptIn(ExperimentalTestApi::class)
class LandingSharedTest {
    @Test
    fun themedLandingRendersAnActionableControl() =
        runComposeUiTest {
            setContent {
                HermesControlTheme(themePreference = ThemePreference.DARK) {
                    LandingScreen(onAuthLogin = {})
                }
            }
            val clickable = onAllNodes(hasClickAction()).fetchSemanticsNodes()
            assertTrue(clickable.isNotEmpty(), "expected the landing screen to expose at least one clickable control")
        }
}
