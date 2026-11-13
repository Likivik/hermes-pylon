package com.m57.hermescontrol

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.m57.hermescontrol.data.model.EnvVarConfig
import com.m57.hermescontrol.theme.HermesControlTheme
import com.m57.hermescontrol.theme.ThemePreference
import com.m57.hermescontrol.theme.ThemePreset
import com.m57.hermescontrol.ui.keys.CategorySection
import com.m57.hermescontrol.ui.keys.KeysScreen
import com.m57.hermescontrol.ui.keys.KeysUiState
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pixel captures of the Keys screen from state alone.
 *
 * The screen is a pure function of [KeysUiState] since the Route/Screen split, which is
 * the point of the split: a row change can be reviewed as pixels instead of argued about.
 *
 * Record: ./gradlew recordRoborazziDebug --tests "*KeysScreenRoboTest*"
 * Verify: ./gradlew verifyRoborazziDebug
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    qualifiers = "w460dp-h920dp-420dpi",
    application = android.app.Application::class,
)
class KeysScreenRoboTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun setUp() {
        FakeAndroidKeyStore.setup
    }

    private fun capture(
        name: String,
        state: KeysUiState,
    ) {
        composeRule.setContent {
            HermesControlTheme(
                themePreference = ThemePreference.DARK,
                useDynamicColors = false,
                themePreset = ThemePreset.DEFAULT,
            ) {
                KeysScreen(
                    state = state,
                    onRefresh = {},
                    onRetry = {},
                    onOpenAddDialog = {},
                    onDismissAddDialog = {},
                    onNewKeyNameChange = {},
                    onNewKeyValueChange = {},
                    onAddKey = {},
                    onRequestDelete = {},
                    onDismissDelete = {},
                    onConfirmDelete = {},
                    onReveal = {},
                    onHide = {},
                    onSaveKey = { _, _ -> },
                    onShowToast = {},
                    onRestartGateway = {},
                )
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/assets/roborazzi/$name.png")
    }

    @Test
    fun keys_populated() = capture("keys-populated", KeysUiState(categories = FakeCategories))

    @Test
    fun keys_empty() = capture("keys-empty", KeysUiState())

    @Test
    fun keys_loading() = capture("keys-loading", KeysUiState(isLoading = true))
}

private val FakeCategories =
    listOf(
        CategorySection(
            name = "LLM Providers",
            vars =
                mapOf(
                    "OPENROUTER_API_KEY" to
                        EnvVarConfig(
                            isSet = true,
                            redactedValue = "sk-or-v1-••••••••••••••••",
                            description = "API key for OpenRouter model routing",
                            category = "LLM Providers",
                            isPassword = true,
                        ),
                    "ANTHROPIC_API_KEY" to
                        EnvVarConfig(
                            isSet = true,
                            redactedValue = "sk-ant-••••••••••••••••",
                            description = "API key for Claude models",
                            category = "LLM Providers",
                            isPassword = true,
                        ),
                ),
            expanded = true,
        ),
        CategorySection(
            name = "Messaging Platforms",
            vars =
                mapOf(
                    "TELEGRAM_BOT_TOKEN" to
                        EnvVarConfig(
                            isSet = false,
                            description = "Bot token from @BotFather used for the gateway",
                            category = "Messaging Platforms",
                            isPassword = true,
                        ),
                ),
            expanded = true,
        ),
    )
