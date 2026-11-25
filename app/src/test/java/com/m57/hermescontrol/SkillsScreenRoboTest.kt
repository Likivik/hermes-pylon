package com.m57.hermescontrol

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.m57.hermescontrol.data.model.Skill
import com.m57.hermescontrol.theme.HermesControlTheme
import com.m57.hermescontrol.theme.ThemePreference
import com.m57.hermescontrol.theme.ThemePreset
import com.m57.hermescontrol.ui.skills.SkillsScreen
import com.m57.hermescontrol.ui.skills.SkillsUiState
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pixel captures of the Skills screen from state alone.
 *
 * Record: ./gradlew recordRoborazziDebug --tests "*SkillsScreenRoboTest*"
 * Verify: ./gradlew verifyRoborazziDebug
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [34],
    qualifiers = "w460dp-h920dp-420dpi",
    application = android.app.Application::class,
)
class SkillsScreenRoboTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun setUp() {
        FakeAndroidKeyStore.setup
    }

    private fun capture(
        name: String,
        state: SkillsUiState,
    ) {
        composeRule.setContent {
            HermesControlTheme(
                themePreference = ThemePreference.DARK,
                useDynamicColors = false,
                themePreset = ThemePreset.DEFAULT,
            ) {
                SkillsScreen(
                    state = state,
                    onRefresh = {},
                    onSearchHub = {},
                    onUpdateFromHub = {},
                    onSetViewMode = {},
                    onToggle = {},
                    onEdit = {},
                    onUninstall = {},
                    onSetSourceFilter = {},
                    onHubQueryChange = {},
                    onClearHubSearch = {},
                    onInstall = {},
                    onPreviewHubSkill = {},
                    onSaveContent = { _, _ -> },
                    onClearEditor = {},
                    onClearSaveSuccess = {},
                    onClearPreview = {},
                )
            }
        }
        composeRule.onRoot().captureRoboImage("src/test/assets/roborazzi/$name.png")
    }

    // Real skill names from this install, with the source values the API returns.
    private val skills =
        listOf(
            Skill(
                name = "hermes-pylon-kmp",
                description = "KMP/CMP work in the hermes-pylon fork: build gates, jj workspaces, Roborazzi goldens.",
                category = "software-development",
                enabled = true,
                source = "built-in",
            ),
            Skill(
                name = "obsidian",
                description = "Read, search, create and edit notes in the Obsidian vault.",
                category = "productivity",
                enabled = true,
                source = "hub",
            ),
            Skill(
                name = "comfyui",
                description = "Generate images, video and audio via diffusion workflows.",
                category = "creative",
                enabled = false,
                source = "hub",
            ),
            Skill(
                name = "email-inbox-triage",
                description = "Triage an inbox: prioritize threads, draft replies safely.",
                category = "email",
                enabled = false,
            ),
        )

    @Test
    fun populated() {
        capture(name = "skills-populated", state = SkillsUiState(skills = skills))
    }

    @Test
    fun empty() {
        capture(name = "skills-empty", state = SkillsUiState())
    }

    @Test
    fun loading() {
        capture(name = "skills-loading", state = SkillsUiState(isLoading = true))
    }
}
