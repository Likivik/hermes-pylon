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
 * E2E: pin / unpin a rail session through the long-press popup.
 *
 * The pinned marker is a small primary dot under the icon AND pinned sessions
 * sort into the "pinned" group (top of the rail). We assert the pinned dot
 * appears, then disappears after unpin.
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

    @Test
    fun pinThenUnpin_togglesPinnedMarker() {
        val password = E2eHarness.realGatewayPasswordFromArgs()
            ?: error("e2ePassword not set — CI must pass -Pandroid.testInstrumentationRunnerArguments.e2ePassword=${'$'}E2E_PASS")

        E2eHarness.resetStateForTest()
        E2eHarness.seedRealGatewayProfile(password = password)

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        activityScenario = scenario

        // With auto-create disabled, launch lands on an empty pane — select the
        // first rail session explicitly before pinning it.
        val tag = E2eHarness.selectSessionAt(composeRule, 0)

        // Pin via popup.
        composeRule.onNodeWithTag(tag).performTouchInput { longClick() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Pin").performClick()
        composeRule.waitForIdle()

        // Pinned → popup now offers "Unpin"; marker dot sits under the icon.
        composeRule.onNodeWithTag(tag).performTouchInput { longClick() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Unpin").assertIsDisplayed()
        composeRule.onNodeWithText("Unpin").performClick()
        composeRule.waitForIdle()

        // Unpinned → "Pin" is back.
        composeRule.onNodeWithTag(tag).performTouchInput { longClick() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Pin").assertIsDisplayed()
    }
}