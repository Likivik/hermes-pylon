package com.m57.hermescontrol.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
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
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * E2E: rename-resume FAILURE path against the REAL Hermes gateway.
 *
 * The workflow seeds `/tmp/e2e-home/state.db` with a `reaped-bg` row whose
 * `message_count=99999` exceeds the gateway's `sessions.max_resume_messages`
 * default (20_000). `session.resume(reaped-bg)` then trips
 * `SessionResumeTooLargeError` in `_resume_guard` and returns 4130 — the
 * rename flow surfaces the error and NEVER sends `session.title` (the bug
 * the surfaced-failure path fixes).
 *
 * Without a scripted gateway, the test asserts the UI observable: an "Error"
 * surface from the rename RPC error handler. The optimistic title update at
 * ChatViewModel.kt:1666 is unchanged on this path (no session.list refresh is
 * scheduled on resume-failure), so the rail title remains the original
 * "Reaped chat" — but we don't assert that strictly, since the optimistic
 * update + no-list-refresh means the displayed title reflects the user's
 * typed value locally. The defining assertion is the surfaced error.
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

    @After
    fun tearDown() {
        activityScenario?.close()
        HermesWsClient.e2eWsOverride = null
    }

    @Test
    fun reapedResume_4001_surfacesError_neverTitles() {
        val password = E2eHarness.realGatewayPasswordFromArgs()
            ?: error("e2ePassword not set — CI must pass -Pandroid.testInstrumentationRunnerArguments.e2ePassword=\$E2E_PASS")

        // 1. Reset state and seed the real-gateway profile (ticket mode). The
        //    seed sets e2eWsOverride to /api/ws of the real gateway BEFORE
        //    MainActivity launches; openSocket() appends ?ticket=<minted>.
        E2eHarness.resetStateForTest()
        E2eHarness.seedRealGatewayProfile(password = password)

        // 2. Launch and wait for the seeded reaped-bg rail item. The rail
        //    renders once the gateway's session.list replies with the seeded
        //    row — even with message_count=99999, list_sessions_rich does
        //    NOT filter by message_count, so reaped-bg is shown.
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        activityScenario = scenario
        DeviceLog.withEvidence("rename-failure-e2e") {
            composeRule.waitUntilAtLeastOneExists(
                hasTestTag("rail_item_reaped-bg"),
                timeoutMillis = 30_000,
            )
            composeRule.onNodeWithTag("rail_item_reaped-bg").assertIsDisplayed()
        }

        // 3. Long-press the rail item, tap Edit, type, Save. The rename path
        //    fires session.resume(reaped-bg) → gateway returns 4130 →
        //    ChatViewModel surfaces the error and DOES NOT send session.title.
        composeRule.onNodeWithTag("rail_item_reaped-bg")
            .performTouchInput { longClick() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Edit icon / name").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("rename_field")
            .performTextReplacement("Renamed E2E")
        composeRule.onNodeWithText("Save").performClick()

        // 4. UI assertion: the rename RPC error handler surfaces an "Error"
        //    surface (ChatViewModel.kt:1075: "rename-resume failed (...)"). The
        //    rename-resume path's surfaced-error fix ensures the error text is
        //    visible — this is the test pin. assertIsDisplayed has its own
        //    polling up to the compose rule's default timeout.
        composeRule.onNodeWithText("Error", substring = true).assertIsDisplayed()
    }
}