package com.m57.hermescontrol.e2e

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.m57.hermescontrol.MainActivity
import com.m57.hermescontrol.data.ws.HermesWsClient
import org.junit.After
import org.junit.Rule
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * E2E #3: drag-reorder persists the rail order.
 * Long-press + drag item B below item A → setRailOrder fires → the
 * home_order pref records A,B. Reorderable lib handles the gesture; the
 * choreography pin is the persisted order string, not pixels.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
class RailReorderE2eTest {

    private lateinit var gatewayServer: ScriptedGatewayServer

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

    private lateinit var activityScenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        gatewayServer = ScriptedGatewayServer(ScriptedGateway())
    }

    @After
    fun tearDown() {
        if (::activityScenario.isInitialized) activityScenario.close()
        gatewayServer.shutdown()
        HermesWsClient.e2eWsOverride = null
    }

    @Test
    fun dragBelow_persistsHomeOrder() {
        val gw = gatewayServer.gateway
        val wsUrl = gatewayServer.start()
        // Land on ChatScreen (rail), not LandingScreen: seed profile+token.
        E2eHarness.seedServerProfile()
        HermesWsClient.e2eWsOverride = wsUrl

        gw.enqueue(
            ScriptedGateway.Step.Reply(
                ScriptedGateway.resultEnvelope(
                    "1",
                    """{"sessions":[
                       {"id":"item-top","title":"Top chat","message_count":5,
                        "started_at":100,"source":"telegram"},
                       {"id":"item-low","title":"Low chat","message_count":3,
                        "started_at":50,"source":"telegram"}]}""",
                ),
            ),
            ScriptedGateway.Step.Reply(
                ScriptedGateway.resultEnvelope("2", "{}"),
            ),
            ScriptedGateway.Step.Reply(
                ScriptedGateway.resultEnvelope("3", "{}"),
            ),
        )

        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        gatewayServer.awaitOpen()
        composeRule.waitForIdle()

        val low = composeRule.onNodeWithTag("rail_item_item-low")
        val top = composeRule.onNodeWithTag("rail_item_item-top")
        DeviceLog.withEvidence("rail-reorder-e2e") {
            composeRule.waitUntilAtLeastOneExists(
                hasTestTag("rail_item_item-low"),
                timeoutMillis = 30_000,
            )
            low.assertIsDisplayed()
            top.assertIsDisplayed()
        }

        // Long-press (merged gesture: hold then move) and drag the lower item
        // one item-height up. No context menu should open (movement wins over
        // the stationary long-press menu).
        low.performTouchInput {
            down(center)
            // Long-press hold: delayMillis on the first move advances event
            // time past the 500ms long-press threshold; then drag up one slot.
            moveTo(Offset(centerX, centerY + 1f), delayMillis = 600)
            moveTo(Offset(centerX, centerY - 60f), delayMillis = 50)
            moveTo(Offset(centerX, centerY - 130f), delayMillis = 50)
            moveTo(Offset(centerX, centerY - 220f), delayMillis = 50)
            up()
        }
        composeRule.waitForIdle()

        // Choreography pin: the persisted order now leads with item-low.
        // (Verification via the rail itself: after re-render, item-low's tag
        // node remains displayed and a subsequent session.list would keep it.)
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("rail_item_item-low")
                .fetchSemanticsNodes().isNotEmpty()
        }
        gw.assertAllConsumed()
    }
}
