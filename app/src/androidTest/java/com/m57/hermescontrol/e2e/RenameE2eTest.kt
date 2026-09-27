package com.m57.hermescontrol.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertDoesNotExist
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
 * E2E: rename a background rail session against the REAL Hermes gateway
 * (CI workflow starts `hermes dashboard` with basic-auth gated mode on
 * 127.0.0.1:8642 and seeds `/tmp/e2e-home/state.db` with stored-current +
 * stored-bg). The app boots, lists the rail, the user long-presses stored-bg,
 * renames it, and:
 *  1. session.resume is sent (the rename-resume path the bug fix introduced)
 *  2. session.title is sent addressed by the STORAGE id (session_key)
 *  3. no error is surfaced
 *  4. the rail title updates to the new name
 *
 * The real gateway drives all RPC choreography — no scripted gateway, no
 * `gw.assertSent()` checks. UI-only assertions verify the rename took effect.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class RenameE2eTest {

    /**
     * Pre-grant runtime permissions the app requests on first launch. Without
     * this, the system permission dialog covers MainActivity on Android 13+
     * (GMD `e2eApi34`), the activity stays in PAUSED state, and
     * `ActivityScenario.launch()` returns before the Compose hierarchy is
     * built — `waitUntilAtLeastOneExists` then polls an empty semantics tree
     * and times out with "No compose hierarchies found".
     */
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
    fun renameBackgroundSession_resumeFirst_thenTitleBySessionKey() {
        // Read the ephemeral CI password; fail loudly if absent (the workflow
        // passes it via -Pandroid.testInstrumentationRunnerArguments.e2ePassword).
        val password = E2eHarness.realGatewayPasswordFromArgs()
            ?: error("e2ePassword not set — CI must pass -Pandroid.testInstrumentationRunnerArguments.e2ePassword=\$E2E_PASS")

        // 1. Wipe process-wide state (HermesWsClient + hermes_rail prefs)
        //    AND log into the real gateway. seedRealGatewayProfile sets the
        //    ticket-mode profile + override BEFORE MainActivity launches, so
        //    the very first connect attempt targets the real /api/ws and
        //    carries a freshly-minted ticket.
        E2eHarness.resetStateForTest()
        E2eHarness.seedRealGatewayProfile(password = password)

        // 2. Launch last. The activity's first composition lands on ChatScreen
        //    (token was published by seedRealGatewayProfile), the rail renders once
        //    session.list returns the seeded rows, and HermesWsClient connects
        //    to ws://127.0.0.1:8642/api/ws?ticket=<minted>.
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        activityScenario = scenario

        // 3. Wait for the rail item that seedRealGatewayProfile guarantees is
        //    in the seeded DB. Without scripting, we trust the real gateway's
        //    session.list reply to surface the rows the workflow seeded.
        DeviceLog.withEvidence("rename-e2e") {
            composeRule.waitUntilAtLeastOneExists(
                hasTestTag("rail_item_stored-bg"),
                timeoutMillis = 30_000,
            )
            composeRule.onNodeWithTag("rail_item_stored-bg")
                .assertIsDisplayed()
        }
        composeRule.onNodeWithTag("rail_item_stored-bg")
            .performTouchInput { longClick() }
        composeRule.waitForIdle()

        // 4. Tap Edit.
        composeRule.onNodeWithText("Edit icon / name").performClick()
        composeRule.waitForIdle()

        // 5. Clear + type the new name, Save.
        composeRule.onNodeWithTag("rename_field")
            .performTextReplacement("Renamed E2E")
        composeRule.onNodeWithText("Save").performClick()

        // 6. UI assertion: the rail item's title updates to the new name (the
        //    optimistic update at ChatViewModel.kt:1666 fires immediately, and
        //    the real gateway's session.list refresh on the title-ack
        //    confirms). No "Error" surface — the rename succeeded.
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("Renamed E2E")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Renamed E2E").assertIsDisplayed()
        composeRule.onNodeWithText("Error", substring = true).assertDoesNotExist()
    }
}