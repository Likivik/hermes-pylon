package com.m57.hermescontrol

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.m57.hermescontrol.data.model.PluginInfo
import com.m57.hermescontrol.data.model.ProviderOption
import com.m57.hermescontrol.theme.HermesControlTheme
import com.m57.hermescontrol.theme.ThemePreference
import com.m57.hermescontrol.theme.ThemePreset
import com.m57.hermescontrol.ui.plugins.PluginsScreen
import com.m57.hermescontrol.ui.plugins.PluginsUiState
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pixel captures of the Plugins screen from state alone.
 *
 * Record: ./gradlew recordRoborazziDebug --tests "*PluginsScreenRoboTest*"
 * Verify: ./gradlew verifyRoborazziDebug
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    qualifiers = "w460dp-h920dp-420dpi",
    application = android.app.Application::class,
)
class PluginsScreenRoboTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun setUp() {
        FakeAndroidKeyStore.setup
    }

    private fun capture(
        name: String,
        state: PluginsUiState,
    ) {
        composeRule.setContent {
            HermesControlTheme(
                themePreference = ThemePreference.DARK,
                useDynamicColors = false,
                themePreset = ThemePreset.DEFAULT,
            ) {
                PluginsScreen(
                    state = state,
                    onRefresh = {},
                    onMemoryProviderChange = {},
                    onContextEngineChange = {},
                    onSaveProviders = {},
                    onInstallUrlChange = {},
                    onInstallForceChange = {},
                    onInstallEnableChange = {},
                    onInstall = {},
                    onTogglePlugin = {},
                    onToggleVisibility = {},
                    onUpdatePlugin = {},
                    onActivatePlugin = {},
                    onRequestRemove = {},
                    onConfirmRemove = {},
                    onCancelRemove = {},
                )
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/assets/roborazzi/$name.png")
    }

    // Shaped like the real catalogue the gateway serves: name, version, source and the
    // capability flags that decide which buttons each row gets.
    private val plugins =
        listOf(
            PluginInfo(
                name = "graphiti",
                description = "Temporal knowledge graph memory with bi-temporal entity tracking.",
                version = "0.9.4",
                source = "git+https://github.com/Likivik/hermes-graphiti-plugin",
                runtimeStatus = "enabled",
                hasDashboardManifest = true,
                canRemove = true,
                canUpdateGit = true,
            ),
            PluginInfo(
                name = "workspace-discipline",
                description = "Binds a session to a repo workspace and blocks edits elsewhere.",
                version = "0.3.1",
                source = "local",
                runtimeStatus = "enabled",
                canRemove = true,
            ),
            PluginInfo(
                name = "petdex",
                description = "Animated mascots for the desktop shell.",
                version = "0.1.0",
                source = "git+https://github.com/Likivik/petdex",
                runtimeStatus = "disabled",
                canRemove = true,
                canUpdateGit = true,
            ),
        )

    @Test
    fun populated() {
        capture(
            name = "plugins-populated",
            state =
                PluginsUiState(
                    plugins = plugins,
                    memoryProvider = "builtin",
                    memoryOptions =
                        listOf(
                            ProviderOption(name = "builtin", description = "Built-in memory"),
                            ProviderOption(name = "graphiti", description = "Graphiti knowledge graph"),
                        ),
                    contextEngine = "compressor",
                    contextOptions = listOf(ProviderOption(name = "compressor", description = "Context compressor")),
                ),
        )
    }

    @Test
    fun empty() {
        capture(name = "plugins-empty", state = PluginsUiState())
    }

    @Test
    fun loading() {
        capture(name = "plugins-loading", state = PluginsUiState(isLoading = true))
    }
}
