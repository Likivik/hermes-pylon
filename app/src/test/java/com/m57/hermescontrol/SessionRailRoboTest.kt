package com.m57.hermescontrol

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.m57.hermescontrol.theme.ThemePreset
import com.m57.hermescontrol.theme.ThemePreference
import com.m57.hermescontrol.theme.HermesControlTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage

/**
 * Roborazzi captures of the C7 rail + chat screen, replacing the layoutlib
 * @PreviewTest pipeline for pixel-truth renders (layoutlib can't reproduce
 * runtime elevation/scrollbar/Noto-APNG behavior; Robolectric can).
 *
 * Record:  ./gradlew recordRoborazziDebug   (writes goldens into repo)
 * Verify:  ./gradlew verifyRoborazziDebug   (CI gate; fails on diff)
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w460dp-h920dp-420dpi")
class SessionRailRoboTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun capture(name: String, dark: Boolean, preset: ThemePreset) {
        composeRule.setContent {
            HermesControlTheme(
                themePreference = if (dark) ThemePreference.DARK else ThemePreference.LIGHT,
                useDynamicColors = false,
                themePreset = preset,
            ) {
                FullScreenRailBody(dark = dark)
            }
        }
        composeRule.onRoot()
            .captureRoboImage("src/test/assets/roborazzi/$name.png")
    }

    @Test
    fun c7_light_default() = capture("c7-light-default", dark = false, preset = ThemePreset.DEFAULT)

    @Test
    fun c7_dark_default() = capture("c7-dark-default", dark = true, preset = ThemePreset.DEFAULT)

    @Test
    fun c7_dark_amoled() = capture("c7-dark-amoled", dark = true, preset = ThemePreset.AMOLED)
}
