package com.m57.hermescontrol

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.m57.hermescontrol.data.model.ProfileInfo
import com.m57.hermescontrol.theme.HermesControlTheme
import com.m57.hermescontrol.theme.ThemePreference
import com.m57.hermescontrol.theme.ThemePreset
import com.m57.hermescontrol.ui.bots.BotsScreen
import com.m57.hermescontrol.ui.bots.BotsUiState
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pixel captures of the Bots screen from state alone.
 *
 * The screen is a pure function of [BotsUiState] since the Route/Screen split, which is
 * the point of the split: a row change can be reviewed as pixels instead of argued about.
 *
 * Record: ./gradlew recordRoborazziDebug --tests "*BotsScreenRoboTest*"
 * Verify: ./gradlew verifyRoborazziDebug
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    qualifiers = "w460dp-h920dp-420dpi",
    application = android.app.Application::class,
)
class BotsScreenRoboTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun setUp() {
        FakeAndroidKeyStore.setup
    }

    private fun capture(
        name: String,
        state: BotsUiState,
    ) {
        composeRule.setContent {
            HermesControlTheme(
                themePreference = ThemePreference.DARK,
                useDynamicColors = false,
                themePreset = ThemePreset.DEFAULT,
            ) {
                BotsScreen(
                    state = state,
                    selectedProfileId = "profile-1",
                    onRefresh = {},
                    onRetry = {},
                    onToggleShowHidden = {},
                    onSearchQueryChange = {},
                    onBotClick = {},
                )
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/assets/roborazzi/$name.png")
    }

    @Test
    fun bots_populated() = capture("bots-populated", BotsUiState(profiles = FakeBots, nowSeconds = 1_700_000_000))

    @Test
    fun bots_empty() = capture("bots-empty", BotsUiState())

    @Test
    fun bots_error() =
        capture(
            "bots-error",
            BotsUiState(errorMessage = "Could not reach the gateway", isLoading = false),
        )

    @Test
    fun bots_loading() = capture("bots-loading", BotsUiState(isLoading = true))
}

private val FakeBots =
    listOf(
        ProfileInfo(name = "aria", display_name = "Aria", description = "Drafts and edits long documents"),
        ProfileInfo(name = "kestrel", display_name = "Kestrel", description = "Nightly repo maintenance and releases"),
        ProfileInfo(name = "mira", description = "Answers tenant questions, files nothing", description_auto = true),
        ProfileInfo(name = "sova", display_name = "Sova", description = "Watches feeds and summarises what changed"),
    )
