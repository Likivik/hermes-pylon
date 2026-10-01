package com.m57.hermescontrol.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
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
 * E2E: rename succeeds against the REAL Hermes gateway.
 *
 * Isolation strategy: the app auto-creates a fresh session on EVERY launch
 * (ChatViewModel: session.create on first connect). So this test picks the
 * FIRST rail item (newest-first sort → the app's own fresh session), renames
 * it, and asserts the title updated + no error surfaced. No RPC-create, no
 * dependence on stored_session_id vs session.list id (the mismatch that
 * burned e2e-69).
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class RenameE2eTest {

    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 1)
    val logcatRule = LogcatOnFailureRule()

    @get:Rule(order = 2)
    val composeRule = createEmptyComposeRule()

    private var activityScenario: ActivityScenario<MainActivity>? = null

    /** Tag of the rail item this test operates on (e.g. rail_item_<id>). */
    private var itemTag: String? = null

    @After
    fun tearDown() {
        activityScenario?.close()
        HermesWsClient.e2eWsOverride = null
        itemTag = null
    }

    /**
     * Wait for at least one rail item to render and return its tag.
     * Delegated to [E2eHarness.waitForFirstRailItem].
     */

    @Test
    fun renameBackgroundSession_resumeFirst_thenTitleBySessionKey() {
        val password = E2eHarness.realGatewayPasswordFromArgs()
            ?: error("e2ePassword not set — CI must pass -Pandroid.testInstrumentationRunnerArguments.e2ePassword=\$E2E_PASS")

        // 1. Wipe process-wide state + log into the real gateway.
        E2eHarness.resetStateForTest()
        E2eHarness.seedRealGatewayProfile(password = password)

        // 2. Launch. App auto-creates its session; rail populates.
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        activityScenario = scenario

        // 3. Grab the FIRST rail item (the app's own fresh session) by tag.
        DeviceLog.withEvidence("rename-e2e:firstItem") {
            itemTag = E2eHarness.waitForFirstRailItem(composeRule)
        }
        val tag = itemTag ?: error("no rail item tag")

        // 4. Long-press, Edit, rename, Save.
        composeRule.onNodeWithTag(tag).performTouchInput { longClick() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Edit icon / name").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("rename_field")
            .performTextReplacement("Renamed E2E")
        composeRule.onNodeWithText("Save").performClick()

        // 5. UI pin #1: the rail title updates to the typed name.
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("Renamed E2E")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Renamed E2E").assertIsDisplayed()

        // 6. UI pin #2: no "Error" surface. After the rename settled, any
        //    "Error" node = real failure (peer-test leak was fixed by per-test
        //    isolation; this test owns its session, no peer writes it).
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Error", substring = true).assertDoesNotExist()
    }
}