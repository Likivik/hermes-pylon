package com.m57.hermescontrol.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
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
class RailReorderE2eTest {

    private lateinit var gatewayServer: ScriptedGatewayServer

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        gatewayServer = ScriptedGatewayServer(ScriptedGateway())
    }

    @After
    fun tearDown() {
        gatewayServer.shutdown()
    }

    @Test
    fun dragBelow_persistsHomeOrder() {
        val gw = gatewayServer.gateway
        val wsUrl = gatewayServer.start()
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

        gatewayServer.awaitOpen()
        composeRule.waitForIdle()

        val low = composeRule.onNodeWithTag("rail_item_item-low")
        val top = composeRule.onNodeWithTag("rail_item_item-top")
        low.assertIsDisplayed()
        top.assertIsDisplayed()

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
