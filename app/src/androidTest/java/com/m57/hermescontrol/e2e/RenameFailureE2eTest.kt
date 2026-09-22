package com.m57.hermescontrol.e2e

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import org.junit.Rule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.m57.hermescontrol.data.ws.HermesWsClient
import com.m57.hermescontrol.MainActivity
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * E2E #2: rename-resume FAILURE path.
 * Gateway reaps the session mid-air → session.resume 4001s → the app must
 * surface the error (errorMessage) and NEVER send session.title (the bug
 * that silently dropped renames before the surfaced-failure fix).
 */
@RunWith(AndroidJUnit4::class)
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
class RenameFailureE2eTest {

    private lateinit var gatewayServer: ScriptedGatewayServer

    @get:Rule(order = 0)
    val logcatRule = LogcatOnFailureRule()

    @get:Rule(order = 1)
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
    fun reapedResume_4001_surfacesError_neverTitles() {
        val gw = gatewayServer.gateway
        val wsUrl = gatewayServer.start()
        HermesWsClient.e2eWsOverride = wsUrl

        gw.enqueue(
            ScriptedGateway.Step.Reply(
                ScriptedGateway.Companion.resultEnvelope(
                    "1",
                    """{"sessions":[
                       {"id":"reaped-bg","title":"Stale chat","message_count":1,
                        "started_at":10,"source":"telegram"}]}""",
                ),
            ),
            ScriptedGateway.Step.Reply(
                ScriptedGateway.Companion.resultEnvelope("2", "{}"),
            ),
            ScriptedGateway.Step.Reply(
                ScriptedGateway.Companion.resultEnvelope("3", "{}"),
            ),
            // session.resume ack — 4001 means "session not found / reaped".
            ScriptedGateway.Step.Reply(
                ScriptedGateway.Companion.errorEnvelope(
                    id = "4",
                    code = 4001,
                    message = "session not found",
                ),
            ),
        )

        E2eHarness.seedServerProfile()
        activityScenario = ActivityScenario.launch(MainActivity::class.java)
        gatewayServer.awaitOpen()
        composeRule.waitForIdle()

        DeviceLog.withEvidence("rename-failure-e2e") {
            composeRule.waitUntilAtLeastOneExists(
                hasTestTag("rail_item_reaped-bg"),
                timeoutMillis = 30_000,
            )
            composeRule.onNodeWithTag("rail_item_reaped-bg").assertIsDisplayed()
        }

        composeRule.onNodeWithTag("rail_item_reaped-bg")
            .performTouchInput { longClick() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Edit icon / name").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("rename_field")
            .performTextReplacement("Renamed E2E")
        composeRule.onNodeWithText("Save").performClick()

        // session.title must NEVER have been sent — the surfaced-error path
        // short-circuits before the title RPC.
        composeRule.waitUntil(5_000) {
            // We expect to have sent session.resume but never session.title.
            gw.assertNeverSent("session.title", mapOf("session_id" to "reaped-bg"))
            true
        }
        gw.assertAllConsumed()

        // Error surfaced — "Error" toast/banner visible.
        composeRule.onNodeWithText("Error", substring = true).assertIsDisplayed()
    }
}