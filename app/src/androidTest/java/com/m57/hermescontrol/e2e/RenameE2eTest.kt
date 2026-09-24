package com.m57.hermescontrol.e2e

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import org.junit.Rule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.m57.hermescontrol.MainActivity
import com.m57.hermescontrol.data.ws.HermesWsClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * E2E: the exact field-bug scenario. App boots against the scripted gateway,
 * user long-presses a background rail session, renames it, and the rename:
 *  1. fires session.resume FIRST (gateway rejects bare storage-id titles)
 *  2. then session.title addressed by the STORAGE id (session_key)
 *  3. shows no error toast
 *  4. persists in the rail list
 */
@RunWith(AndroidJUnit4::class)
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
class RenameE2eTest {

    private lateinit var gatewayServer: ScriptedGatewayServer

    /**
     * Pre-grant runtime permissions the app requests on first launch. Without
     * this, the system permission dialog covers MainActivity on Android 13+
     * (GMD `e2eApi34`), the activity stays in PAUSED state, and
     * `ActivityScenario.launch()` returns before the Compose hierarchy is
     * built — `waitUntilAtLeastOneExists` then polls an empty semantics tree
     * and times out with "No compose hierarchies found".
     *
     * On API < 33 [GrantPermissionRule] no-ops (POST_NOTIFICATIONS isn't
     * runtime on older versions), so this rule is safe across GMD images.
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
    fun renameBackgroundSession_resumeFirst_thenTitleBySessionKey() {
        // 1. Reset HermesWsClient.requestId (Kotlin object — survives across
        //    androidTest classes in the same instrumentation JVM) and wipe
        //    hermes_rail prefs so last_session from the prior test doesn't
        //    reroute handleGatewayReady into switchSession(staleId).
        E2eHarness.resetStateForTest()
        // 2. Script the choreography: rail loads (session.list), then the
        //    rename path: resume → ack(session_key) → title accepted.
        val gw = gatewayServer.gateway
        // Mock first (port allocation), then route the app's WS client at it.
        // HermesWsClient is in RECONNECTING (from MainActivity.onStart's failed
        // attempt against the seeded wsUrl); the backoff retry (1s..30s) picks
        // up e2eWsOverride on its next tick — gatewayServer.awaitOpen() below
        // waits up to 30s for that.
        val wsUrl = gatewayServer.start()

        gw.enqueue(
            // session.list ack: two sessions; stored-bg is NOT current.
            ScriptedGateway.Companion.sessionList(
                """[
                   {"id":"stored-current","title":"Current chat","message_count":5,
                    "started_at":0,"source":"telegram"},
                   {"id":"stored-bg","title":"Background chat","message_count":3,
                    "started_at":0,"source":"telegram"}]""",
            ),
            // commands.catalog ack — empty catalog is fine.
            ScriptedGateway.Companion.commandsCatalogEmpty(),
            // The rename resume: ack carries session_key = storage id.
            ScriptedGateway.Companion.resumeAck("stored-bg", "runtime-9hex"),
            // The title (addressed by storage id) is accepted.
            ScriptedGateway.Companion.titleAccept("Renamed E2E"),
        )

        // 3. Seed the profile+token AFTER scripting — the seed publish must
        //    not preempt the scripted gateway.ready flow.
        E2eHarness.seedServerProfile()
        // 4. e2eWsOverride after seed: the WS connect that runs in
        //    MainActivity.onStart consumes the override URL on its next retry.
        HermesWsClient.e2eWsOverride = wsUrl

        // 5. Launch last. ActivityScenario.launch returns once CREATED;
        //    awaitOpen() blocks until the WS handshake completes.
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        gatewayServer.awaitOpen()
        composeRule.waitForIdle()
        // DIAGNOSTIC: print what the app actually sent before asserting, so a
        // red run shows the RPC order without needing the 30s timeout.
        android.util.Log.i("PylonE2E", "RPCs received so far: ${gw.dumpState()}")

        // 6. Long-press the background rail item → context menu.
        DeviceLog.withEvidence("rename-e2e", extra = { gw.dumpState() }) {
            composeRule.waitUntilAtLeastOneExists(
                hasTestTag("rail_item_stored-bg"),
                timeoutMillis = 30_000,
            )
            composeRule.onNodeWithTag("rail_item_stored-bg")
                .assertIsDisplayed()
        }
        composeRule.onNodeWithTag("rail_item_stored-bg")
            .performTouchInput { longClick() }
        composeRule.waitForIdle()

        // 7. Tap Edit.
        composeRule.onNodeWithText("Edit icon / name").performClick()
        composeRule.waitForIdle()

        // 8. Clear + type the new name, Save.
        composeRule.onNodeWithTag("rename_field")
            .performTextReplacement("Renamed E2E")
        composeRule.onNodeWithText("Save").performClick()

        // 9. The choreography assertions — the heart of the field bug.
        composeRule.waitUntil(10_000) {
            // session.title sent, addressed by STORAGE id, and accepted.
            gw.assertSent("session.title", mapOf("session_id" to "stored-bg"))
            true
        }
        gw.assertNeverSent(
            "session.title",
            mapOf("session_id" to "runtime-9hex"),
        )
        gw.assertAllConsumed()

        // 10. Rail shows the new name; no error toast.
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Renamed E2E")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Renamed E2E").assertIsDisplayed()
        composeRule.onNodeWithText("Error", substring = true).assertDoesNotExist()
    }
}