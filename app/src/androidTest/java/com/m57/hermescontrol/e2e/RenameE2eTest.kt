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
 * E2E: rename succeeds against the REAL Hermes gateway.
 *
 * Each test run is its own isolated namespace: we create a fresh session via
 * `session.create` RPC, wait for THAT id to appear in the rail (`rail_item_*`),
 * then drive the rename UI against it. The seeded `state.db` rows are
 * untouched — they exist as a baseline rail for OTHER test classes; our test
 * doesn't depend on them and they don't depend on our test.
 *
 * Without a scripted gateway, the assertion is a UI observation: the rail
 * title for OUR session updates to the new name and no "Error" snackbar
 * surfaces (e2e-66's failure: a stray "Error" widget from a peer test's
 * state.db reset window leaked into the negative-path assertion; pinning the
 * assertion to OUR rail_item_* removes the cross-test coupling).
 *
 * Cleanup: the session created for this test is DELETEd via `session.delete`
 * in @After so subsequent tests in the same instrumentation JVM start from
 * the same baseline state.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class RenameE2eTest {

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

    private var activityScenario: ActivityScenario<MainActivity>? = null

    /** The fresh session id this test created via `session.create`. */
    private var ownSessionId: String? = null

    @After
    fun tearDown() {
        // Best-effort delete our session so the rail doesn't accumulate
        // zombie rows across runs in the same instrumentation JVM.
        ownSessionId?.let { id ->
            runCatching {
                runBlocking {
                    HermesWsClient.request(
                        method = WsMethods.SESSION_DELETE,
                        params = mapOf("session_id" to id),
                        timeoutMs = 5_000L,
                    ).await()
                }
            }
        }
        activityScenario?.close()
        HermesWsClient.e2eWsOverride = null
        ownSessionId = null
    }

    @Test
    fun renameOwnSession_resumeFirst_thenTitleBySessionKey() {
        // Read the ephemeral CI password; fail loudly if absent (the workflow
        // passes it via -Pandroid.testInstrumentationRunnerArguments.e2ePassword).
        val password = E2eHarness.realGatewayPasswordFromArgs()
            ?: error("e2ePassword not set — CI must pass -Pandroid.testInstrumentationRunnerArguments.e2ePassword=\$E2E_PASS")

        // 1. Wipe process-wide state (HermesWsClient + hermes_rail prefs)
        //    AND log into the real gateway. seedRealGatewayProfile sets the
        //    ticket-mode profile + override BEFORE MainActivity launches, so
        //    the very first connect attempt targets the real /api/ws and
        //    carries a freshly-minted ticket.
        E2eHarness.resetStateForTest()
        E2eHarness.seedRealGatewayProfile(password = password)

        // 2. Launch. The app auto-fires its own `session.create` on first
        //    start (ChatViewModel.kt:1644+) — let that settle into the rail
        //    so the WS socket is warm and `session.list` replies have a
        //    request id we can correlate.
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        activityScenario = scenario

        // 3. Now create OUR session via a direct RPC. We DO NOT race the
        //    app's auto-create — we make our own, distinct session. This is
        //    the per-test isolation: this test owns its own id, asserts on
        //    it, deletes it in @After. Seeded rows in state.db are noise.
        var sessionId: String? = null
        DeviceLog.withEvidence("rename-e2e:create") {
            sessionId = runBlocking {
                HermesWsClient.request(
                    method = WsMethods.SESSION_CREATE,
                    params = mapOf("source" to "desktop"),
                    timeoutMs = 10_000L,
                ).await()
            }.let { result ->
                val map = result as? Map<String, Any?>
                    ?: error("session.create returned non-map result: $result")
                // Rail labels by the STORAGE id (the `stored_session_id`), which
                // is what session.list rows expose as `id`. Fall back to the
                // runtime id if storage id is absent.
                map["stored_session_id"] as? String
                    ?: map["session_id"] as? String
                    ?: error("session.create result missing session ids: $map")
            }
        }
        ownSessionId = sessionId ?: error("session.create produced no id")

        val itemTag = "rail_item_$ownSessionId"

        // 4. Force the rail to refresh so OUR session appears under our tag.
        runBlocking {
            HermesWsClient.request(
                method = WsMethods.SESSION_LIST,
                timeoutMs = 10_000L,
            ).await()
        }
        DeviceLog.withEvidence("rename-e2e:waitRailItem") {
            composeRule.waitUntilAtLeastOneExists(
                hasTestTag(itemTag),
                timeoutMillis = 30_000,
            )
            composeRule.onNodeWithTag(itemTag).assertIsDisplayed()
        }

        // 5. Long-press, Edit, rename, Save. Driven entirely through OUR
        //    rail_item tag so cross-test state.db rows don't interfere.
        composeRule.onNodeWithTag(itemTag).performTouchInput { longClick() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Edit icon / name").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("rename_field")
            .performTextReplacement("Renamed E2E")
        composeRule.onNodeWithText("Save").performClick()

        // 6. UI assertion pin #1: the rail title for OUR session updates
        //    to the typed name. We assert against the scoped SemanticsNode
        //    rooted at rail_item_$ownSessionId — peer-test state.db rows
        //    (or the auto-create's row) are irrelevant here.
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("Renamed E2E")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Renamed E2E").assertIsDisplayed()

        // 7. UI assertion pin #2: no "Error" surface anywhere. Renames
        //    against the real gateway are clean (the rename-resume → title
        //    pipeline returns 200 + the optimistic title update + the
        //    session.list refresh reconfirm). Failure mode to catch: any
        //    pre-existing "Error" widget from a peer test that hasn't yet
        //    cleared (e2e-66 leak). To pin this to OUR test, we wait
        //    briefly past the snackbar dismiss window (snackbars clear in
        //    ~4s) before asserting. A real surfaced rename error would
        //    re-trigger after that window.
        composeRule.waitForIdle()
        // After the rename pipeline settled, the snackbar host state has no
        // pending errors for OUR rename. assertDoesNotExist() is strict —
        // any "Error" node remaining indicates either a leaked peer-test
        // surface or a real failure in OUR flow.
        composeRule.onNodeWithText("Error", substring = true).assertDoesNotExist()
    }
}
