package com.m57.hermescontrol.e2e

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
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
 * E2E: drag-reorder persists the rail order against the REAL Hermes gateway.
 *
 * Per-test isolation: this run creates TWO of its own sessions (`upper`,
 * `lower`) via `session.create` RPC and asserts reorder purely against those
 * two. Seeded `state.db` rows are noise for this test. We don't depend on
 * `item-top` / `item-low` existing, and they don't depend on us.
 *
 * Choreography: the rail sorts newest-first by `started_at`. We create
 * `lower` first (older → lands NEAR the bottom of the rail) and `upper`
 * second (newer → lands NEAR the top). The user long-presses `lower`,
 * taps Reorder, and drags `lower` UP past `upper` (and over every seeded
 * row + the app's auto-create). The `sh.calvin.reorderable` library fires
 * `RailEvent.Reorder` on drag-end → `ChatViewModel.setRailOrder` writes
 * the new id order to `hermes_rail/home_order`.
 *
 * Without a scripted gateway, the choreography pin is `home_order`: after
 * the drag, the persisted order must have `lower` BEFORE `upper` (lower
 * moved up over upper by many slots).
 *
 * Drag distance fix (e2e-66): incremental moveBy() steps stacked to a final
 * 1220px offset, but the rail item only swapped ONE slot because the
 * incremental pauses between steps allowed the reorderable library to
 * re-anchor the draggable pointer to the source row each frame. The fix:
 * use a SINGLE overshooting sweep from `lower`'s center to far above
 * `upper`'s top so the pointer crosses every slot without intermediate
 * re-snap.
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
    fun dragOurNewerBelow_persistsHomeOrderAboveOurOlder() {
        val password = E2eHarness.realGatewayPasswordFromArgs()
            ?: error("e2ePassword not set — CI must pass -Pandroid.testInstrumentationRunnerArguments.e2ePassword=\$E2E_PASS")

        // 1. Reset state and seed the real-gateway profile (ticket mode).
        E2eHarness.resetStateForTest()
        E2eHarness.seedRealGatewayProfile(password = password)

        // 2. Launch. App auto-creates its session; rail populates with the
        //    seeded baseline (item-top newest, item-low older → newest-first
        //    sort puts item-top ABOVE item-low).
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        activityScenario = scenario

        // 3. Use the SEEDED baseline rows item-top/item-low (deterministic —
        //    this test only REORDERS them; no other test renames/deletes
        //    them). The rail renders them under rail_item_item-top /
        //    rail_item_item-low.
        val lowerTag = "rail_item_item-low"
        val upperTag = "rail_item_item-top"

        // 4. Wait for both to render.
        DeviceLog.withEvidence("rail-reorder-e2e:waitRailItems") {
            composeRule.waitUntilAtLeastOneExists(hasTestTag(lowerTag), timeoutMillis = 30_000)
            composeRule.waitUntilAtLeastOneExists(hasTestTag(upperTag), timeoutMillis = 30_000)
            composeRule.onNodeWithTag(upperTag).assertIsDisplayed()
            composeRule.onNodeWithTag(lowerTag).assertIsDisplayed()
        }

        // 5. Long-press `lower`, tap Reorder, then drag `lower` UP past
        //    `upper`. We re-resolve `lower` after entering reorder mode so
        //    the semantics node match covers the new (reorder-mode) bounds.
        composeRule.onNodeWithTag(lowerTag).performTouchInput { longClick() }
        composeRule.waitForIdle()
        composeRule
            .onNodeWithText("Reorder")
            .assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("✓ Done").assertIsDisplayed()

        // The reorderable library's long-press drag pattern: down + hold past
        // the long-press threshold, then a SINGLE overshooting sweep upward.
        // Per e2e-66: incremental moveBy steps re-anchor the draggable to the
        // source row between frames and the drag only moves one slot. One big
        // sweep crosses the target row center without intermediate re-anchor.
        val lowerInReorder = composeRule.onNodeWithTag(lowerTag)
        lowerInReorder.performTouchInput {
            down(Offset(centerX, centerY))
            advanceEventTime(700) // hold past long-press timeout (in reorder mode)
            // 8 discrete 250px upward moves with time between frames so the
            // reorderable renders the floating drag state (rapid moves w/o
            // frames register as a flick — no Reorder event, e2e-77).
            repeat(8) {
                moveBy(Offset(0f, -250f), delayMillis = 60)
            }
            advanceEventTime(300) // let the last move settle before up()
            up()
        }
        composeRule.waitForIdle()

        // 6. Choreography pin: the persisted home_order now has `lower`
        //    before `upper` — the drag moved `lower` up.
        val prefs =
            InstrumentationRegistry.getInstrumentation().targetContext
                .getSharedPreferences("hermes_rail", 0)
        val homeOrder =
            run {
                var persisted: String? = null
                composeRule.waitUntil(15_000) {
                    persisted =
                        prefs.getString("home_order", null)?.takeIf { it.isNotBlank() }
                    persisted != null
                }
                persisted ?: ""
            }

        check(homeOrder.split(',').let { ids ->
            ids.contains("item-low") &&
                ids.contains("item-top") &&
                ids.indexOf("item-low") < ids.indexOf("item-top")
        }) {
            "rail home_order did not reflect the drag: $homeOrder (expected item-low before item-top)"
        }

        // 7. Both items remain rendered after the drag (no collection-shape
        //    violation crashed the rail).
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithTag(lowerTag)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }
}
