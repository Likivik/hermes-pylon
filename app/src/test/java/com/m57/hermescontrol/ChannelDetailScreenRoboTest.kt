package com.m57.hermescontrol

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.m57.hermescontrol.data.model.MessagingPlatform
import com.m57.hermescontrol.theme.HermesControlTheme
import com.m57.hermescontrol.theme.ThemePreference
import com.m57.hermescontrol.theme.ThemePreset
import com.m57.hermescontrol.ui.channels.ChannelDetailScreen
import com.m57.hermescontrol.ui.channels.ChannelsUiState
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pixel captures of one channel's page, from state alone.
 *
 * Record: ./gradlew recordRoborazziDebug --tests "*ChannelDetailScreenRoboTest*"
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    qualifiers = "w460dp-h920dp-420dpi",
    application = android.app.Application::class,
)
class ChannelDetailScreenRoboTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun setUp() {
        FakeAndroidKeyStore.setup
    }

    private fun capture(
        name: String,
        state: ChannelsUiState,
        platformId: String,
    ) {
        composeRule.setContent {
            HermesControlTheme(
                themePreference = ThemePreference.DARK,
                useDynamicColors = false,
                themePreset = ThemePreset.DEFAULT,
            ) {
                ChannelDetailScreen(
                    state = state,
                    platformId = platformId,
                    onBack = {},
                    onTest = {},
                    onSave = {},
                    onRemove = {},
                    onStartOnboarding = {},
                )
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/assets/roborazzi/$name.png")
    }

    @Test
    fun channel_detail_connected() =
        capture("channel-detail-connected", ChannelsUiState(platforms = listOf(ConnectedTelegram)), "telegram")

    @Test
    fun channel_detail_setup() =
        capture("channel-detail-setup", ChannelsUiState(platforms = listOf(UnconfiguredTelegram)), "telegram")
}

private val ConnectedTelegram =
    MessagingPlatform(
        id = "telegram",
        name = "Telegram",
        description = "Home channel and direct messages via the bot",
        enabled = true,
        configured = true,
        gatewayRunning = true,
        state = "connected",
    )

private val UnconfiguredTelegram =
    MessagingPlatform(
        id = "telegram",
        name = "Telegram",
        description = "Home channel and direct messages via the bot",
        enabled = false,
        configured = false,
        gatewayRunning = true,
        state = "not_configured",
    )
