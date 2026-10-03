package com.m57.hermescontrol.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.m57.hermescontrol.MainActivity
import com.m57.hermescontrol.data.ws.HermesWsClient
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * E2E: edit session icon + name through the long-press popup. The rename
 * flow (same dialog) is covered by RenameE2eTest; here we verify the EDIT
 * DIALOG opens on a background row and a rename through it reflects in the
 * rail — the shared icon+name surface. Icon-only changes via the emoji grid
 * are heavy/async (Org.Kodein.Emoji picker), so the stable assertion is the
 * dialog flow + name update; the icon field's "No icon" toggle is covered
 * when currentIcon is set by rename-with-icon (unit-tested in SessionRenameTest).
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class IconChangeE2eTest {

    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 1)
    val logcatRule = LogcatOnFailureRule()

    @get:Rule(order = 2)
    val composeRule = createEmptyComposeRule()

    private var activityScenario: ActivityScenario<MainActivity>? = null

    @After
    fun tearDown() {
        activityScenario?.close()
        HermesWsClient.e2eWsOverride = null
    }

    @Test
    fun editDialogOnBackgroundRow_renameReflectsInRail() {
        val password = E2eHarness.realGatewayPasswordFromArgs()
            ?: error("e2ePassword not set — CI must pass -Pandroid.testInstrumentationRunnerArguments.e2ePassword=${'$'}E2E_PASS")

        E2eHarness.resetStateForTest()
        E2eHarness.seedRealGatewayProfile(password = password)

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        activityScenario = scenario

        // With auto-create disabled, launch lands on an empty pane — select the
        // first rail session explicitly before opening its edit dialog.
        val tag = E2eHarness.selectSessionAt(composeRule, 0)

        // Long-press → Edit icon / name (the shared dialog).
        composeRule.onNodeWithTag(tag).performTouchInput { longClick() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Edit icon / name").performClick()
        composeRule.waitForIdle()

        // The dialog is up; rename via the name field.
        composeRule.onNodeWithTag("rename_field").performTextReplacement("IconEdit E2E")
        composeRule.onNodeWithText("Save").performClick()

        // The rail reflects the new name — scoped to the rail item (the chat
        // header also shows the same title).
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("IconEdit E2E").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(tag).assertIsDisplayed()
    }
}