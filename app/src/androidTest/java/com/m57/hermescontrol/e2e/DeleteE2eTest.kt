package com.m57.hermescontrol.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
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
 * E2E: delete a rail session through the long-press popup. Deleting a
 * NON-active session (the app's fresh auto-create is active; we delete a
 * seeded background row) removes it from the rail.
 *
 * The gateway refuses to delete the ACTIVE session ("cannot delete an
 * active session"), and the UI guard also blocks deleting the current one —
 * so this targets a seeded background item (stored-bg), never the active one.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class DeleteE2eTest {

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
    fun deleteBackgroundSession_removesFromRail() {
        val password = E2eHarness.realGatewayPasswordFromArgs()
            ?: error("e2ePassword not set — CI must pass -Pandroid.testInstrumentationRunnerArguments.e2ePassword=${'$'}E2E_PASS")

        E2eHarness.resetStateForTest()
        E2eHarness.seedRealGatewayProfile(password = password)

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        activityScenario = scenario

        // Wait for the seeded background row to render.
        val bgTag = "rail_item_stored-bg"
        composeRule.waitUntilAtLeastOneExists(
            androidx.compose.ui.test.hasTestTag(bgTag),
            timeoutMillis = 30_000,
        )

        // Long-press → Delete → confirm not-auto: the popup's Delete fires
        // immediately (no confirm dialog), then the row disappears from the rail.
        composeRule.onNodeWithTag(bgTag).performTouchInput { longClick() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Delete").performClick()
        composeRule.waitForIdle()

        // Rail no longer shows the deleted row.
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("Background chat")
                .fetchSemanticsNodes().isEmpty()
        }
    }
}