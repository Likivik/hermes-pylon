package com.m57.hermescontrol

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.m57.hermescontrol.data.model.OAuthProvider
import com.m57.hermescontrol.data.model.OAuthProviderStatus
import com.m57.hermescontrol.theme.HermesControlTheme
import com.m57.hermescontrol.theme.ThemePreference
import com.m57.hermescontrol.theme.ThemePreset
import com.m57.hermescontrol.ui.providers.ProvidersScreen
import com.m57.hermescontrol.ui.providers.ProvidersUiState
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pixel captures of the Providers screen from state alone.
 *
 * Record: ./gradlew recordRoborazziDebug --tests "*ProvidersScreenRoboTest*"
 * Verify: ./gradlew verifyRoborazziDebug
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    qualifiers = "w460dp-h920dp-420dpi",
    application = android.app.Application::class,
)
class ProvidersScreenRoboTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun setUp() {
        FakeAndroidKeyStore.setup
    }

    private fun capture(
        name: String,
        state: ProvidersUiState,
    ) {
        composeRule.setContent {
            HermesControlTheme(
                themePreference = ThemePreference.DARK,
                useDynamicColors = false,
                themePreset = ThemePreset.DEFAULT,
            ) {
                ProvidersScreen(
                    state = state,
                    onRefresh = {},
                    onStartFlow = {},
                    onDisconnect = {},
                    onCodeChange = {},
                    onSubmitCode = {},
                    onCancelFlow = {},
                    onDismissFlow = {},
                    onOpenBrowser = {},
                )
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/assets/roborazzi/$name.png")
    }

    // Taken from the live catalogue on this machine (GET /api/providers/oauth), not invented:
    // same ids, names, flows, disconnect rules and source labels the app really receives.
    // Two are flipped to logged_in so the golden shows both row states.
    private val providers =
        listOf(
            OAuthProvider(
                id = "nous",
                name = "Nous Portal",
                flow = "device_code",
                cliCommand = "hermes auth add nous",
                docsUrl = "https://portal.nousresearch.com",
                status =
                    OAuthProviderStatus(
                        loggedIn = true,
                        source = "nous_portal",
                        sourceLabel = "https://portal.nousresearch.com",
                        hasRefreshToken = true,
                    ),
            ),
            OAuthProvider(
                id = "openai-codex",
                name = "ChatGPT or Codex Subscription",
                flow = "device_code",
                cliCommand = "hermes auth add openai-codex",
                docsUrl = "https://platform.openai.com/docs",
                status = OAuthProviderStatus(loggedIn = false, source = "openai_codex", sourceLabel = "OpenAI Codex"),
            ),
            OAuthProvider(
                id = "qwen-oauth",
                name = "Qwen (via Qwen CLI)",
                flow = "external",
                cliCommand = "hermes auth add qwen-oauth",
                docsUrl = "https://github.com/QwenLM/qwen-code",
                disconnectHint = "Managed by that provider's CLI; remove it there.",
                disconnectable = false,
                status =
                    OAuthProviderStatus(
                        loggedIn = false,
                        source = "qwen_cli",
                        sourceLabel = "/var/lib/hermes/.qwen/oauth_creds.json",
                    ),
            ),
            OAuthProvider(
                id = "minimax-oauth",
                name = "MiniMax (OAuth)",
                flow = "device_code",
                cliCommand = "hermes auth add minimax-oauth",
                docsUrl = "https://www.minimax.io",
                status =
                    OAuthProviderStatus(
                        loggedIn = true,
                        source = "minimax_oauth",
                        sourceLabel = "MiniMax (global)",
                        hasRefreshToken = true,
                    ),
            ),
            OAuthProvider(
                id = "xai-oauth",
                name = "xAI Grok OAuth (SuperGrok / Premium+)",
                flow = "device_code",
                cliCommand = "hermes auth add xai-oauth",
                docsUrl = "https://hermes-agent.nousresearch.com/docs/guides/xai-grok-oauth",
                status =
                    OAuthProviderStatus(
                        loggedIn = false,
                        source = "xai_oauth",
                        sourceLabel = "/var/lib/hermes/.hermes/auth.json",
                    ),
            ),
            OAuthProvider(
                id = "anthropic",
                name = "Anthropic Account",
                flow = "external",
                cliCommand = "hermes auth add anthropic",
                status = OAuthProviderStatus(loggedIn = false),
            ),
            OAuthProvider(
                id = "claude-code",
                name = "Anthropic OAuth: Required Extra Usage Settings",
                flow = "external",
                cliCommand = "hermes auth add claude-code",
                disconnectHint = "Managed outside Hermes — run the CLI to remove it.",
                disconnectable = false,
                status = OAuthProviderStatus(loggedIn = false),
            ),
        )

    @Test
    fun populated() {
        capture(
            name = "providers-populated",
            state = ProvidersUiState(isLoading = false, providers = providers),
        )
    }

    @Test
    fun empty() {
        capture(name = "providers-empty", state = ProvidersUiState())
    }

    @Test
    fun loading() {
        capture(name = "providers-loading", state = ProvidersUiState(isLoading = true))
    }
}
