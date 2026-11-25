package com.m57.hermescontrol

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.m57.hermescontrol.data.model.McpServer
import com.m57.hermescontrol.theme.HermesControlTheme
import com.m57.hermescontrol.theme.ThemePreference
import com.m57.hermescontrol.theme.ThemePreset
import com.m57.hermescontrol.ui.mcp.McpActions
import com.m57.hermescontrol.ui.mcp.McpServersScreen
import com.m57.hermescontrol.ui.mcp.McpServersUiState
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pixel captures of the MCP servers screen from state alone.
 *
 * Record: ./gradlew recordRoborazziDebug --tests "*McpServersScreenRoboTest*"
 * Verify: ./gradlew verifyRoborazziDebug
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    qualifiers = "w460dp-h920dp-420dpi",
    application = android.app.Application::class,
)
class McpServersScreenRoboTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun setUp() {
        FakeAndroidKeyStore.setup
    }

    /** Every action is a no-op: the screen must render from state alone. */
    private val noActions =
        object : McpActions {
            override fun loadServers() = Unit

            override fun testAllServers() = Unit

            override fun toggleServer(server: McpServer) = Unit

            override fun testServer(name: String) = Unit

            override fun deleteServer(name: String) = Unit

            override fun toggleAddForm() = Unit

            override fun setAddMode(mode: com.m57.hermescontrol.ui.mcp.AddServerMode) = Unit

            override fun updateAddServerName(v: String) = Unit

            override fun updateAddServerUrl(v: String) = Unit

            override fun updateAddServerCommand(v: String) = Unit

            override fun updateAddServerArgs(v: String) = Unit

            override fun updateAddServerAuth(v: String) = Unit

            override fun updateAddServerBearerToken(v: String) = Unit

            override fun submitAddServer() = Unit

            override fun startEditingEnv(server: McpServer) = Unit

            override fun stopEditingEnv() = Unit

            override fun updateEnvKey(v: String) = Unit

            override fun updateEnvValue(v: String) = Unit

            override fun addEnvVar(serverName: String) = Unit

            override fun removeEnvVar(
                serverName: String,
                key: String,
            ) = Unit

            override fun loadCatalog() = Unit

            override fun updateCatalogQuery(v: String) = Unit

            override fun installCatalogEntry(entry: com.m57.hermescontrol.data.model.McpCatalogEntry) = Unit

            override fun updateCatalogEnvVar(
                key: String,
                value: String,
            ) = Unit

            override fun startMcpOAuthFlow(
                server: McpServer,
                onOpenBrowser: (String) -> Boolean,
            ) = Unit

            override fun retryMcpOAuthBrowser(onOpenBrowser: (String) -> Boolean) = Unit

            override fun dismissOAuthFlow() = Unit

            override fun clearToast() = Unit
        }

    private fun capture(
        name: String,
        state: McpServersUiState,
    ) {
        composeRule.setContent {
            HermesControlTheme(
                themePreference = ThemePreference.DARK,
                useDynamicColors = false,
                themePreset = ThemePreset.DEFAULT,
            ) {
                McpServersScreen(state = state, actions = noActions)
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/assets/roborazzi/$name.png")
    }

    private val servers =
        listOf(
            McpServer(
                name = "nextcloud",
                transport = "http",
                url = "https://cloud.example.com/mcp",
                enabled = true,
                status = "connected",
                tools = listOf("list_files", "upload_file", "search"),
            ),
            McpServer(
                name = "trilium",
                transport = "stdio",
                command = "npx",
                args = listOf("-y", "triliumnext-mcp"),
                enabled = true,
                status = "connected",
                tools = listOf("search_notes", "create_note"),
            ),
            McpServer(
                name = "touchdesigner",
                transport = "stdio",
                command = "python3",
                enabled = false,
                error = "process not running",
            ),
        )

    @Test
    fun populated() {
        capture(name = "mcp-populated", state = McpServersUiState(servers = servers))
    }

    @Test
    fun empty() {
        capture(name = "mcp-empty", state = McpServersUiState())
    }

    @Test
    fun loading() {
        capture(name = "mcp-loading", state = McpServersUiState(isLoading = true))
    }
}
