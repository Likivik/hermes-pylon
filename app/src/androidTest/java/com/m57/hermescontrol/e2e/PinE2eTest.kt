package com.m57.hermescontrol.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
 * E2E: pin / unpin a rail session through the long-press popup.
 *
 * The pin CONTRACT is the marker dot under the icon (`pinned_dot`) plus the
 * "pinned" sort group — NOT the popup's label. This test previously asserted
 * only that the menu said "Unpin" instead of "Pin", which proves nothing about
 * the pin itself (the docstring claimed the dot was checked; the code never
 * did). It now counts the real marker, and checks it survives a full activity
 * recreate, because pins live in `hermes_rail` prefs.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class PinE2eTest {
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

    /** Pinned markers rendered right now. The dot is a child node → unmerged. */
    private fun pinnedDotCount(): Int =
        composeRule.onAllNodesWithTag("pinned_dot", useUnmergedTree = true)
            .fetchSemanticsNodes().size

    @Test
    fun pinThenUnpin_togglesPinnedMarker() {
        val password =
            E2eHarness.realGatewayPasswordFromArgs()
                ?: error(
                    "e2ePassword not set — CI must pass " +
                        "-Pandroid.testInstrumentationRunnerArguments.e2ePassword=${'$'}E2E_PASS",
                )

        E2eHarness.resetStateForTest()
        E2eHarness.seedRealGatewayProfile(password = password)

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        activityScenario = scenario

        // With auto-create disabled, launch lands on an empty pane — select the
        // first rail session explicitly before pinning it.
        val tag = E2eHarness.selectSessionAt(composeRule, 0)

        // Precondition: nothing is pinned in this fresh run.
        composeRule.waitUntil(10_000) { pinnedDotCount() == 0 }

        // Pin via popup.
        composeRule.onNodeWithTag(tag).performTouchInput { longClick() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Pin").performClick()
        composeRule.waitForIdle()

        // The MARKER appears. Counting it is the assertion that can fail; the
        // popup label would flip even if the pin never landed.
        composeRule.waitUntil(10_000) { pinnedDotCount() == 1 }

        // PERSISTENCE: pins are a client pref, so a full restart must keep it.
        scenario.recreate()
        E2eHarness.waitForFirstRailItem(composeRule)
        composeRule.waitUntil(15_000) { pinnedDotCount() == 1 }

        // Unpin → popup offers "Unpin" this time, and the marker goes away.
        composeRule.onNodeWithTag(tag).performTouchInput { longClick() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Unpin").assertIsDisplayed()
        composeRule.onNodeWithText("Unpin").performClick()
        composeRule.waitUntil(10_000) { pinnedDotCount() == 0 }

        // And the popup offers "Pin" again.
        composeRule.onNodeWithTag(tag).performTouchInput { longClick() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Pin").assertIsDisplayed()
    }
}
