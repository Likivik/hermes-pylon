package com.m57.hermescontrol.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
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
 * E2E: the archive (stale) drawer — chip shows the stale count, tapping it
 * reveals the archived (ended) session rows, tapping again hides them.
 *
 * Staleness is automatic (ended/old sessions); there is no explicit "archive"
 * user action in the popup. The seed inserts `stale-bg` with an old ended_at
 * so it lands in the archive group.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class ArchiveE2eTest {

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
    fun archiveChipTogglesStaleDrawer() {
        val password = E2eHarness.realGatewayPasswordFromArgs()
            ?: error("e2ePassword not set — CI must pass -Pandroid.testInstrumentationRunnerArguments.e2ePassword=\\$E2E_PASS")

        E2eHarness.resetStateForTest()
        E2eHarness.seedRealGatewayProfile(password = password)

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        activityScenario = scenario

        // The stale row (seeded `stale-bg`, old ended_at) starts hidden; the
        // chip shows the count. Wait for the chip (may be bottom of the rail).
        composeRule.waitUntil(30_000) {
            composeRule.onAllNodesWithText("old·1").fetchSemanticsNodes().isNotEmpty()
        }

        // Open the drawer → the stale row's title appears.
        composeRule.onNodeWithText("old·1").performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("Stale chat").fetchSemanticsNodes().isNotEmpty()
        }

        // Close → gone again.
        composeRule.onNodeWithText("old·1").performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("Stale chat").fetchSemanticsNodes().isEmpty()
        }
    }
}