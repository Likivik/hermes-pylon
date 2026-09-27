package com.m57.hermescontrol.e2e

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
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
 * E2E: drag-reorder persists the rail order against the REAL Hermes gateway.
 *
 * The workflow seeds `/tmp/e2e-home/state.db` with item-top (NEWEST) +
 * item-low (OLDER). The rail sorts newest-first by `started_at`, so item-top
 * is initially above item-low. The user long-presses item-low and drags it up
 * past item-top; the `sh.calvin.reorderable` library fires
 * `RailEvent.Reorder` on drag-end → `ChatViewModel.setRailOrder(...)`
 * persists the new order to `hermes_rail/home_order` SharedPreferences.
 *
 * Without a scripted gateway, the choreography pin is the persisted order:
 * after the drag, read `hermes_rail` prefs and assert
 * `home_order.indexOf("item-low") < home_order.indexOf("item-top")`.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class RailReorderE2eTest {

    /**
     * Pre-grant runtime permissions the app requests on first launch. Without
     * this, the system permission dialog covers MainActivity on Android 13+
     * (GMD `e2eApi34`), the activity stays in PAUSED state, and
     * `ActivityScenario.launch()` returns before the Compose hierarchy is
     * built.
     */
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
    fun dragBelow_persistsHomeOrder() {
        val password = E2eHarness.realGatewayPasswordFromArgs()
            ?: error("e2ePassword not set — CI must pass -Pandroid.testInstrumentationRunnerArguments.e2ePassword=\$E2E_PASS")

        // 1. Reset state and seed the real-gateway profile (ticket mode).
        E2eHarness.resetStateForTest()
        E2eHarness.seedRealGatewayProfile(password = password)

        // 2. Launch. The rail renders once session.list returns the seeded
        //    item-top + item-low rows.
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        activityScenario = scenario

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

        // 3. Long-press (merged gesture: hold then move) and drag the lower
        //    item up. With 5 seeded sessions + 1 auto-created = 6 rail items,
        //    item-low sits roughly in the middle; we drag it past item-top
        //    (and the items between them) until it settles above item-top.
        //    The library swaps items as the dragged item's center crosses
        //    each neighbor's center — so the final relative order is what we
        //    assert, not the pixel-perfect drag distance.
        low.performTouchInput {
            down(center)
            // Long-press hold: delayMillis on the first move advances event
            // time past the 500ms long-press threshold; then drag up several
            // slots. The 1px move right after the hold engages the drag.
            moveTo(Offset(centerX, centerY + 1f), delayMillis = 600)
            moveTo(Offset(centerX, centerY - 60f), delayMillis = 50)
            moveTo(Offset(centerX, centerY - 150f), delayMillis = 50)
            moveTo(Offset(centerX, centerY - 250f), delayMillis = 50)
            moveTo(Offset(centerX, centerY - 380f), delayMillis = 50)
            up()
        }
        composeRule.waitForIdle()

        // 4. Choreography pin: the persisted order now leads with item-low.
        //    Verification: read hermes_rail/home_order SharedPreferences. After
        //    the drag end, RailEvent.Reorder fires setRailOrder(...) which
        //    writes the full displayed order to that pref. We poll briefly
        //    because the persist is async (apply(), not commit()).
        val prefs =
            InstrumentationRegistry.getInstrumentation().targetContext
                .getSharedPreferences("hermes_rail", 0)
        val homeOrder =
            run {
                var persisted: String? = null
                composeRule.waitUntil(5_000) {
                    persisted =
                        prefs.getString("home_order", null)?.takeIf { it.isNotBlank() }
                    persisted != null
                }
                persisted ?: ""
            }

        // item-low must appear before item-top — the drag moved it up.
        check(homeOrder.split(',').let { ids ->
            ids.contains("item-low") &&
                ids.contains("item-top") &&
                ids.indexOf("item-low") < ids.indexOf("item-top")
        }) {
            "rail home_order did not reflect the drag: $homeOrder"
        }

        // 5. After the drag, both items remain rendered (drag didn't crash
        //    the rail, no collection shape violation).
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("rail_item_item-low")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }
}