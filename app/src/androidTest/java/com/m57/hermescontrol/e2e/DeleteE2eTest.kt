package com.m57.hermescontrol.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
 * E2E: delete a rail session through the long-press popup. Deleting a
 * NON-active session (a seeded background row) removes it from the rail.
 *
 * The gateway refuses to delete the ACTIVE session ("cannot delete an
 * active session"), and the UI guard also blocks deleting the current one —
 * so this targets a seeded background item (stored-bg), never the active one.
 * With auto-create disabled the rail starts with the seeded rows only, ordered
 * by last activity: index 0 = stored-current, index 1 = stored-bg (the target).
 *
 * Assertions are TAG-based, never text-based: the rail renders a derived label
 * rather than the raw seed title, so the old text lookup for "Background chat"
 * matched nothing — which made the "row is gone" assertion vacuous.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class DeleteE2eTest {

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
    fun deleteBackgroundSession_removesFromRail() {
        val password = E2eHarness.realGatewayPasswordFromArgs()
            ?: error("e2ePassword not set — CI must pass -Pandroid.testInstrumentationRunnerArguments.e2ePassword=${'$'}E2E_PASS")

        E2eHarness.resetStateForTest()
        E2eHarness.seedRealGatewayProfile(password = password)

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        activityScenario = scenario

        // The seeded background row is rail index 1 (the rail orders seeded rows
        // by last activity: [0] = stored-current, [1] = stored-bg). Grab its
        // ACTUAL rail tag — never derive it from the seeded id (e2e-69: the
        // seeded DB id ≠ the rail tag id).
        val bgTag = E2eHarness.waitForRailItemAt(composeRule, index = 1)

        // PRESENCE precondition. Every later assertion is that this row is GONE,
        // and a "gone" check passes trivially when the row was never there — so
        // pin that it exists first. (The old text-based absence check was exactly
        // that bug: the rail renders a derived label, not the seed title
        // "Background chat", so the lookup matched nothing either way.)
        composeRule.onNodeWithTag(bgTag).assertIsDisplayed()

        // Long-press → Delete (fires immediately, no confirm dialog).
        composeRule.onNodeWithTag(bgTag).performTouchInput { longClick() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Delete").performClick()
        composeRule.waitForIdle()

        // Optimistic local removal — proves nothing on its own, because
        // deleteRailSession drops the row BEFORE the gateway answers.
        composeRule.waitUntil(10_000) { railRowCount(bgTag) == 0 }

        // SERVER TRUTH: re-list from the gateway. A refused delete (4007 unknown
        // id / 4023 still-live with a failed close-retry) comes straight back —
        // this is the assertion that can actually fail.
        E2eHarness.refreshSessions(composeRule)
        composeRule.waitUntil(15_000) { railRowCount(bgTag) == 0 }
        composeRule.onNodeWithText("Delete failed", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Error (session", substring = true).assertDoesNotExist()
    }

    /** Rows currently rendered under [tag] (0 = the row is gone). */
    private fun railRowCount(tag: String): Int =
        composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().size
}