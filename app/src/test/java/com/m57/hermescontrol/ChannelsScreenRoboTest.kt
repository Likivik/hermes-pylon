package com.m57.hermescontrol

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.m57.hermescontrol.data.model.MessagingPlatform
import com.m57.hermescontrol.theme.HermesControlTheme
import com.m57.hermescontrol.theme.ThemePreference
import com.m57.hermescontrol.theme.ThemePreset
import com.m57.hermescontrol.ui.channels.ChannelsScreen
import com.m57.hermescontrol.ui.channels.ChannelsUiState
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pixel captures of the Channels screen from state alone.
 *
 * Record: ./gradlew recordRoborazziDebug --tests "*ChannelsScreenRoboTest*"
 * Verify: ./gradlew verifyRoborazziDebug
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    qualifiers = "w460dp-h920dp-420dpi",
    application = android.app.Application::class,
)
class ChannelsScreenRoboTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun setUp() {
        FakeAndroidKeyStore.setup
    }

    private fun capture(
        name: String,
        state: ChannelsUiState,
    ) {
        composeRule.setContent {
            HermesControlTheme(
                themePreference = ThemePreference.DARK,
                useDynamicColors = false,
                themePreset = ThemePreset.DEFAULT,
            ) {
                ChannelsScreen(
                    state = state,
                    onRefresh = {},
                    onRetry = {},
                    onToggle = { _, _ -> },
                    onTest = {},
                    onSave = { _, _ -> },
                    onRemove = {},
                    onStartOnboarding = {},
                    onRestartGateway = {},
                    onDismissOnboarding = {},
                    onAddAllowedId = {},
                    onRemoveAllowedId = {},
                    onNewAllowedIdChange = {},
                    onApplyOnboarding = {},
                )
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/assets/roborazzi/$name.png")
    }

    @Test
    fun channels_populated() = capture("channels-populated", ChannelsUiState(platforms = FakePlatforms))

    @Test
    fun channels_restart_banner() =
        capture(
            "channels-restart-banner",
            ChannelsUiState(platforms = FakePlatforms, restartNeeded = true),
        )

    @Test
    fun channels_empty() = capture("channels-empty", ChannelsUiState())

    @Test
    fun channels_loading() = capture("channels-loading", ChannelsUiState(isLoading = true))
}

private val FakePlatforms =
    listOf(
        MessagingPlatform(
            id = "telegram",
            name = "Telegram",
            description = "Home channel and direct messages via the bot",
            enabled = true,
            configured = true,
            gatewayRunning = true,
            state = "connected",
        ),
        MessagingPlatform(
            id = "slack",
            name = "Slack",
            description = "Workspace channels via a bot token",
            enabled = false,
            configured = true,
            gatewayRunning = true,
            state = "disabled",
        ),
        MessagingPlatform(
            id = "whatsapp",
            name = "WhatsApp",
            description = "Group chats through the bridge",
            enabled = false,
            configured = false,
            gatewayRunning = true,
            state = "unconfigured",
        ),
    )
