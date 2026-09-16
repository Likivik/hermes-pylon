package com.m57.hermescontrol.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.m57.hermescontrol.MainActivity
import com.m57.hermescontrol.data.ws.HermesWsClient
import org.junit.After
import org.junit.Rule
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
class RenameFailureE2eTest {

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
    fun reapedResume_4001_surfacesError_neverTitles() {
        val gw = gatewayServer.gateway
        val wsUrl = gatewayServer.start()
        // Land on ChatScreen (rail), not LandingScreen: seed profile+token.
        E2eHarness.seedServerProfile()
        HermesWsClient.e2eWsOverride = wsUrl

        gw.enqueue(
            // session.list: two sessions, stored-gone is background.
            ScriptedGateway.Step.Reply(
                ScriptedGateway.resultEnvelope(
                    "1",
                    """{"sessions":[
                       {"id":"stored-live","title":"Current chat","message_count":5,
                        "started_at":100,"source":"telegram"},
                       {"id":"stored-gone","title":"Doomed chat","message_count":3,
                        "started_at":50,"source":"telegram"}]}""",
                ),
            ),
            ScriptedGateway.Step.Reply(
                ScriptedGateway.resultEnvelope("2", "{}"),
            ),
            ScriptedGateway.Step.Reply(
                ScriptedGateway.resultEnvelope("3", "{}"),
            ),
            // The rename resume: gateway has REAPED the runtime → 4001.
            ScriptedGateway.Step.Fail(4001, "session not found"),
            // NO further steps: any session.title would be caught by
            // assertNeverSent below + assertAllConsumed.
        )

        gatewayServer.awaitOpen()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("rail_item_stored-gone").assertIsDisplayed()
        composeRule.onNodeWithTag("rail_item_stored-gone")
            .performTouchInput { longClick() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Edit icon / name").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("rename_field")
            .performTextReplacement("Won't land")
        composeRule.onNodeWithText("Save").performClick()

        // Error must surface (the silent-drop regression made this invisible).
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("Error", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        // And the title must never be sent — the choreography pin.
        gw.assertNeverSent("session.title")
        gw.assertAllConsumed()
    }
}
