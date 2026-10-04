package com.m57.hermescontrol.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
 * E2E: tapping a (non-active) rail session switches the active session.
 *
 * The rail marks the active row with the HermesPurple tint, which is NOT
 * readable from semantics — the previous version "asserted" only that the row
 * it had just tapped was still displayed, which is true before the tap too. It
 * therefore passed even if the tap did nothing at all.
 *
 * The real signal is that the tapped session becomes the one the chat pane is
 * bound to: its title moves into the top-bar header (`chat_title`), so the
 * seeded row's label must appear a SECOND time after the tap (rail + header).
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class SwitchE2eTest {

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
    fun tapBackgroundRow_switchesActiveSession() {
        val password = E2eHarness.realGatewayPasswordFromArgs()
            ?: error("e2ePassword not set — CI must pass -Pandroid.testInstrumentationRunnerArguments.e2ePassword=${'$'}E2E_PASS")

        E2eHarness.resetStateForTest()
        E2eHarness.seedRealGatewayProfile(password = password)

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        activityScenario = scenario

        // The seeded background row is rail index 1 (the rail sorts the seeded
        // rows by last activity: [0] = stored-current, [1] = stored-bg titled
        // "Background chat"). Grab its ACTUAL rail tag — never derive it from
        // the seeded id (e2e-69: the seeded DB id ≠ the rail tag id).
        val bgTag = E2eHarness.waitForRailItemAt(composeRule, index = 1)
        composeRule.onNodeWithTag(bgTag).assertIsDisplayed()

        // Before the tap its title exists ONCE (the rail row only).
        val label = "Background chat"
        val before = composeRule.onAllNodesWithText(label).fetchSemanticsNodes().size
        check(before == 1) {
            "expected '$label' to render exactly once (rail row) before switching, found $before — " +
                "is the seed/order still [0]=stored-current, [1]=stored-bg?"
        }

        composeRule.onNodeWithTag(bgTag).performClick()

        // After the tap the chat pane is bound to that session, so its title
        // also lands in the top-bar header → two nodes, and the header itself
        // exists. This is what fails when the tap does nothing.
        composeRule.waitUntil(20_000) {
            composeRule.onAllNodesWithText(label).fetchSemanticsNodes().size >= 2
        }
        composeRule.onNodeWithTag("chat_title", useUnmergedTree = true)
            .assertIsDisplayed()
    }
}
