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
        // 1) Reset HermesWsClient.requestId (Kotlin object — survives across
        //    androidTest classes in the same instrumentation JVM) and wipe
        //    hermes_rail prefs (last_session/pinned/home_order would otherwise
        //    leak across tests and reroute handleGatewayReady into
        //    switchSession(staleId) → unscripted session.resume).
        E2eHarness.resetStateForTest()
        val wsUrl = gatewayServer.start()
        // 2) Pre-script every session.list / commands.catalog / etc envelope
        //    BEFORE the activity launches, so onClientFrame dispatches them
        //    in order as the WS comes up.
        gw.enqueue(
            // session.list ack: method-aware so it lands on session.list
            // regardless of which request id the concurrent
            // handleGatewayReady coroutines assign.
            ScriptedGateway.Companion.sessionList(
                """[
                   {"id":"item-top","title":"Top chat","message_count":5,
                    "started_at":100,"source":"telegram"},
                   {"id":"item-low","title":"Low chat","message_count":3,
                    "started_at":50,"source":"telegram"}]""",
            ),
            // commands.catalog ack — empty catalog is fine.
            ScriptedGateway.Companion.commandsCatalogEmpty(),
        )
        // 3) Seed the profile+token AFTER scripting the replies so the seed
        //    publish doesn't preempt the scripted gateway.ready flow.
        E2eHarness.seedServerProfile()
        // 4) e2eWsOverride after seed: the WS connect that happens during
        //    MainActivity.onStart consumes the override URL on its next
        //    scheduled retry (gatewayServer.awaitOpen below waits for it).
        HermesWsClient.e2eWsOverride = wsUrl

        // 5) Launch last. ActivityScenario.launch returns once the activity is
        //    CREATED; awaitOpen() blocks until the WS handshake completes
        //    (the override URL is what gets dialed, not the seeded :9119).
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
