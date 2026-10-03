package com.m57.hermescontrol.e2e

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.m57.hermescontrol.MainActivity
import com.m57.hermescontrol.data.ws.HermesWsClient
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * E2E: a drag-reorder PERSISTS across activity recreation — the order is
 * saved to SharedPreferences (hermes_rail/home_order) by setRailOrder on
 * drop, and re-applied on the next launch.
 *
 * Same choreography as RailReorderE2eTest (seeded item-top/item-low, drag
 * item-low UP past item-top), then:
 *   1. assert home_order has item-low before item-top (persisted),
 *   2. recreate() the activity,
 *   3. assert the RENDERED rail still shows item-low ABOVE item-top
 *      (home_order survived, not reset to newest-first sort).
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
            ?: error("e2ePassword not set — CI must pass -Pandroid.testInstrumentationRunnerArguments.e2ePassword=${'$'}E2E_PASS")

        E2eHarness.resetStateForTest()
        E2eHarness.seedRealGatewayProfile(password = password)

        var scenario = ActivityScenario.launch(MainActivity::class.java)
        activityScenario = scenario

        val lowerTag = "rail_item_item-low"
        val upperTag = "rail_item_item-top"

        composeRule.waitUntilAtLeastOneExists(hasTestTag(lowerTag), timeoutMillis = 30_000)
        composeRule.waitUntilAtLeastOneExists(hasTestTag(upperTag), timeoutMillis = 30_000)

        // Enter reorder mode, drag item-low UP past item-top (proven pattern).
        composeRule.onNodeWithTag(lowerTag).performTouchInput { longClick() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Reorder").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("✓ Done").assertIsDisplayed()

        val lowerInReorder = composeRule.onNodeWithTag(lowerTag)
        val rowPx = lowerInReorder.fetchSemanticsNode().size.height.toFloat()
        lowerInReorder.performTouchInput {
            down(Offset(centerX, centerY))
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            repeat(6) {
                moveBy(Offset(0f, -rowPx), delayMillis = 100)
                advanceEventTime(150)
            }
            advanceEventTime(1000)
            up()
        }
        composeRule.waitForIdle()

        // 1. Persisted order now has item-low before item-top.
        val prefs =
            InstrumentationRegistry.getInstrumentation().targetContext
                .getSharedPreferences("hermes_rail", 0)
        var homeOrder: String = ""
        composeRule.waitUntil(15_000) {
            homeOrder = prefs.getString("home_order", null) ?: ""
            homeOrder.isNotBlank() &&
                homeOrder.split(',').let { ids ->
                    ids.contains("item-low") &&
                        ids.contains("item-top") &&
                        ids.indexOf("item-low") < ids.indexOf("item-top")
                }
        }

        // 2. Recreate — fresh activity should re-apply home_order.
        scenario.recreate()
        composeRule.waitForIdle()

        // 3. Rendered rail still shows item-low ABOVE item-top (y-position).
        composeRule.waitUntilAtLeastOneExists(hasTestTag(lowerTag), timeoutMillis = 30_000)
        composeRule.waitUntilAtLeastOneExists(hasTestTag(upperTag), timeoutMillis = 30_000)
        composeRule.waitUntil(15_000) {
            val lowY = composeRule.onNodeWithTag(lowerTag)
                .fetchSemanticsNode().positionInRoot.y
            val topY = composeRule.onNodeWithTag(upperTag)
                .fetchSemanticsNode().positionInRoot.y
            lowY < topY
        }
    }
}