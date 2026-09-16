package com.m57.hermescontrol.e2e

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onAllNodesWithText
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
 * E2E: the exact field-bug scenario. App boots against the scripted gateway,
 * user long-presses a background rail session, renames it, and the rename:
 *  1. fires session.resume FIRST (gateway rejects bare storage-id titles)
 *  2. then session.title addressed by the STORAGE id (session_key)
 *  3. shows no error toast
 *  4. persists in the rail list
 */
@RunWith(AndroidJUnit4::class)
class RenameE2eTest {

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
    fun renameBackgroundSession_resumeFirst_thenTitleBySessionKey() {
        // 1. Script the choreography: rail loads (session.list), then the
        //    rename path: resume → ack(session_key) → title accepted.
        val gw = gatewayServer.gateway
        val wsUrl = gatewayServer.start()
        // Route the app's real WS client at the scripted gateway.
        HermesWsClient.e2eWsOverride = wsUrl

        gw.enqueue(
            // session.list ack: two sessions; stored-bg is NOT current.
            ScriptedGateway.Step.Reply(
                ScriptedGateway.Companion.resultEnvelope(
                    "1",
                    """{"sessions":[
                       {"id":"stored-current","title":"Current chat","message_count":5,
                        "started_at":100,"source":"telegram"},
                       {"id":"stored-bg","title":"Background chat","message_count":3,
                        "started_at":50,"source":"telegram"}]}""",
                ),
            ),
            ScriptedGateway.Step.Reply(
                ScriptedGateway.Companion.resultEnvelope("2", "{}"),
            ),
            ScriptedGateway.Step.Reply(
                ScriptedGateway.Companion.resultEnvelope("3", "{}"),
            ),
            // The rename resume: ack carries session_key = storage id.
            ScriptedGateway.Companion.resumeAck("4", "stored-bg", "runtime-9hex"),
            // The title (addressed by storage id) is accepted.
            ScriptedGateway.Companion.titleAccept("5", "Renamed E2E"),
        )

        gatewayServer.awaitOpen()
        composeRule.waitForIdle()

        // 2. Long-press the background rail item → context menu.
        composeRule.onNodeWithTag("rail_item_stored-bg")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("rail_item_stored-bg")
            .performTouchInput { longClick() }
        composeRule.waitForIdle()

        // 3. Tap Edit.
        composeRule.onNodeWithText("Edit icon / name").performClick()
        composeRule.waitForIdle()

        // 4. Clear + type the new name, Save.
        composeRule.onNodeWithTag("rename_field")
            .performTextReplacement("Renamed E2E")
        composeRule.onNodeWithText("Save").performClick()

        // 5. The choreography assertions — the heart of the field bug.
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

        // 6. Rail shows the new name; no error toast.
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Renamed E2E")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Renamed E2E").assertIsDisplayed()
        composeRule.onNodeWithText("Error", substring = true).assertDoesNotExist()
    }
}
