package com.m57.hermescontrol.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.m57.hermescontrol.MainActivity
import com.m57.hermescontrol.data.ws.HermesWsClient
import com.m57.hermescontrol.data.ws.WsMethods
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * E2E: rename-resume FAILURE path against the REAL Hermes gateway.
 *
 * Per-test isolation: each run creates its own session via `session.create`
 * RPC, immediately DELETEs it (`session.delete`) so the gateway has no record
 * of the id. The next `session.list` reply excludes the deleted row, but the
 * rail still shows the row in its optimistic local state (the rail list is
 * only refreshed on explicit `session.list` requests, which ChatViewModel does
 * NOT auto-fire after a rename). We use that visibility window to drive the
 * rename UI for the now-nonexistent id — `session.resume` returns 4007
 * "session not found", the rename-resume error handler
 * (ChatViewModel.kt:1075) detects `pendingRenameTitle != null` AND method ==
 * `session.resume`, and surfaces the error via the snackbar host with text
 * `Error (session.resume): session not found`.
 *
 * We assert against the snackbar text "Error (session.resume)" specifically —
 * that scopes the assertion to the rename pipeline's surface, so any
 * unrelated "Error" widget from a peer test can't false-positive (the e2e-66
 * failure mode). The session.delete is the trigger that turns the real
 * gateway's resume contract into 4007.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class RenameFailureE2eTest {

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

    /** Session id created for this test (and immediately deleted via RPC). */
    private var ownSessionId: String? = null

    @After
    fun tearDown() {
        // The session we created was already DELETEd mid-test, so the
        // @After cleanup is just teardown — no second delete needed.
        activityScenario?.close()
        HermesWsClient.e2eWsOverride = null
        ownSessionId = null
    }

    @Test
    fun renameOwnSession_afterDeleteResumeReturns4007_surfacesError() {
        val password = E2eHarness.realGatewayPasswordFromArgs()
            ?: error("e2ePassword not set — CI must pass -Pandroid.testInstrumentationRunnerArguments.e2ePassword=\$E2E_PASS")

        // 1. Reset state and seed the real-gateway profile (ticket mode).
        E2eHarness.resetStateForTest()
        E2eHarness.seedRealGatewayProfile(password = password)

        // 2. Launch. Wait briefly for the WS connect to settle (the app's
        //    auto-create + session.list land first) — those use the seeded
        //    baseline rows. Our own session/list round-trip below rides the
        //    same WS session.
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        activityScenario = scenario

        // 3. Create our session through the real gateway, then DELETE it
        //    before driving the rename UI. After the delete, any future
        //    `session.resume(<our_id>)` from the app returns 4007.
        var sessionId: String? = null
        DeviceLog.withEvidence("rename-failure-e2e:create") {
            sessionId = runBlocking {
                HermesWsClient.request(
                    method = WsMethods.SESSION_CREATE,
                    params = mapOf("source" to "desktop"),
                    timeoutMs = 10_000L,
                ).await()
            }.let { result ->
                val map = result as? Map<String, Any?>
                    ?: error("session.create returned non-map result: $result")
                // The rail tags by the SESSION.LIST id — which is NOT always
                // stored_session_id (e2e-69). So we don't trust this id for
                // the rail tag; we only keep it for the delete RPC below.
                map["session_id"] as? String
                    ?: map["stored_session_id"] as? String
                    ?: error("session.create result missing session ids: $map")
            }
        }
        ownSessionId = sessionId ?: error("session.create produced no id")

        // 4. Grab the FIRST rail item's tag by UI matcher. This is the app's OWN
        //    auto-created session (newest-first sort → first is freshest). We
        //    deliberately do NOT correlate with the RPC id (stored_session_id
        //    vs session.list id mismatch burned e2e-69) — the UI tag is the
        //    source of truth for what the rail renders.
        val itemTag = E2eHarness.waitForFirstRailItem(composeRule)

        // 5. Delete OUR created session server-side. The rail still shows it
        //    (no session.list refresh after delete) — that stale row is the
        //    rename target: its resume now 4007s.
        runBlocking {
            HermesWsClient.request(
                method = WsMethods.SESSION_DELETE,
                params = mapOf("session_id" to ownSessionId!!),
                timeoutMs = 10_000L,
            ).await()
        }

        // 6. Long-press, Edit, rename, Save. The rename pipeline fires
        //    session.resume(ownId) → gateway returns 4007 "session not found"
        //    → the rename-resume handler logs the failure AND falls through
        //    to the surfaced-error branch at ChatViewModel.kt:1082 that sets
        //    errorMessage = "Error (session.resume): session not found".
        //    The Compose lifecycle effect (ChatLifecycleEffects.kt:140)
        //    pumps that into the snackbar host.
        composeRule.onNodeWithTag(itemTag).performTouchInput { longClick() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Edit icon / name").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("rename_field")
            .performTextReplacement("Renamed E2E")
        composeRule.onNodeWithText("Save").performClick()

        // 7. UI assertion pin: the rename-resume error snackbar carries text
        //    "Error (session.resume)" — scoping to the rename pipeline's
        //    surface rules out unrelated "Error" widgets (snackbars from
        //    peer tests, e.g. e2e-66's leak mode). Substring match catches
        //    the trailing "session not found" message variant. Poll because
        //    the snackbar shows after the round-trip completes.
        DeviceLog.withEvidence("rename-failure-e2e:assertError") {
            composeRule.waitUntil(15_000) {
                composeRule.onAllNodesWithText("Error (session.resume)", substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Error (session.resume)", substring = true)
                .assertIsDisplayed()
        }
    }
}
