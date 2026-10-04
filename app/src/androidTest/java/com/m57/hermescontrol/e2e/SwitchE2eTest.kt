package com.m57.hermescontrol.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
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
 * E2E: tapping a (non-active) rail session switches the active session.
 *
 * The rail marks the active row with the HermesPurple tint, which is NOT
 * readable from semantics — the previous version "asserted" only that the row
 * it had just tapped was still displayed, which is true before the tap too. It
 * therefore passed even if the tap did nothing at all.
 *
 * The real signal is that the tapped session becomes the one the chat pane is
 * bound to: the top-bar header (`chat_title`) stops showing the unbound-pane
 * default title and shows that session's own title instead.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class SwitchE2eTest {

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
    fun tapBackgroundRow_switchesActiveSession() {
        val password = E2eHarness.realGatewayPasswordFromArgs()
            ?: error("e2ePassword not set — CI must pass -Pandroid.testInstrumentationRunnerArguments.e2ePassword=${'$'}E2E_PASS")

        E2eHarness.resetStateForTest()
        E2eHarness.seedRealGatewayProfile(password = password)

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        activityScenario = scenario

        // The seeded background row is rail index 1 (the rail orders seeded rows
        // by last activity: [0] = stored-current, [1] = stored-bg). Grab its
        // ACTUAL rail tag — never derive it from the seeded id, and never match
        // the row by TEXT: the rail renders a derived label rather than the seed
        // title, which is how the previous version managed to assert nothing.
        val bgTag = E2eHarness.waitForRailItemAt(composeRule, index = 1)
        composeRule.onNodeWithTag(bgTag).assertIsDisplayed()

        // BEFORE: with auto-create disabled and no session selected the pane is
        // unbound, so the top-bar header carries the default title.
        composeRule.waitUntil(10_000) { defaultHeaderTitleCount() >= 1 }
        val beforeDefault = defaultHeaderTitleCount()

        composeRule.onNodeWithTag(bgTag).performClick()

        // AFTER: the pane binds to the tapped session, so the header's default
        // title is replaced by that session's own title — the count DROPS. If the
        // tap did nothing (undetectable in the previous version) it stays put.
        composeRule.waitUntil(20_000) { defaultHeaderTitleCount() < beforeDefault }
        composeRule.onNodeWithTag("chat_title", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    /** Rendered nodes carrying the unbound-pane default header title. */
    private fun defaultHeaderTitleCount(): Int =
        composeRule.onAllNodesWithText("Hermes").fetchSemanticsNodes().size
}
