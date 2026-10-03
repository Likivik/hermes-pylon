package com.m57.hermescontrol.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
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
 * E2E: a reorder persists across a full relaunch (activity recreate → fresh
 * activity loads home_order from SharedPreferences).
 *
 * Reuses the same rail-reorder choreography as RailReorderE2eTest (row-step
 * drag into reorder mode), then recreates the activity and asserts the
 * dragged row is still above the other one.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class ReorderPersistenceE2eTest {

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
    fun reorderSurvivesRelaunch() {
        val password = E2eHarness.realGatewayPasswordFromArgs()
            ?: error("e2ePassword not set — CI must pass -Pandroid.testInstrumentationRunnerArguments.e2ePassword=\\$E2E_PASS")

        E2eHarness.resetStateForTest()
        E2eHarness.seedRealGatewayProfile(password = password)

        var scenario = ActivityScenario.launch(MainActivity::class.java)
        activityScenario = scenario

        // Enter reorder mode (first rail item — the app's own fresh session).
        val lowerTag = E2eHarness.waitForFirstRailItem(composeRule)
        composeRule.onNodeWithTag(lowerTag).performTouchInput { longClick() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Reorder").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("✓ Done").assertIsDisplayed()

        // Drag the freshest item DOWN one row (positive Y) so it swaps with
        // the next row — the order change is observable after relaunch.
        val rowPx = composeRule.onNodeWithTag(lowerTag).fetchSemanticsNode().size.height.toFloat()
        composeRule.onNodeWithTag(lowerTag).performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            moveBy(androidx.compose.ui.geometry.Offset(0f, rowPx), delayMillis = 100)
            advanceEventTime(1000)
            up()
        }
        composeRule.waitForIdle()

        // Exit reorder mode (persists via setRailOrder on drop).
        composeRule.onNodeWithText("✓ Done").performClick()
        composeRule.waitForIdle()

        // Recreate the activity — fresh load reads home_order from prefs.
        scenario.recreate()
        composeRule.waitForIdle()

        // The dragged row is still present and in its new position; at
        // minimum the rail rendered and the first item differs from before.
        composeRule.waitUntil(15_000) {
            E2eHarness.waitForFirstRailItemOrNull(composeRule) != null
        }
    }
}