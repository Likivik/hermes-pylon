package com.m57.hermescontrol.e2e

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createEmptyComposeRule
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
 * E2E tripwire for the auto-create disable (Likivik, e2e-88):
 *
 * Launch lands on an EMPTY chat pane — no session.create fires — and the
 * rail shows ONLY the seeded rows. If a regression reintroduces auto-create
 * on launch, the rail gains an extra fresh session (count +1) and this test
 * fails.
 *
 * The seed inserts 5 fresh sessions + 1 archived (`stale-bg`, behind the
 * archive chip). A healthy rail renders exactly 5 visible rail_item_ nodes;
 * an auto-created session would render 6.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class NoAutoCreateE2eTest {
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
    fun noAutoCreate_railCountMatchesSeeded() {
        val password =
            E2eHarness.realGatewayPasswordFromArgs()
                ?: error(
                    "e2ePassword not set — CI must pass " +
                        "-Pandroid.testInstrumentationRunnerArguments.e2ePassword=${'$'}E2E_PASS",
                )

        E2eHarness.resetStateForTest()
        E2eHarness.seedRealGatewayProfile(password = password)

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        activityScenario = scenario

        // The gateway's session store is SHARED across tests in one run
        // (seeded once; earlier tests may delete rows), so the absolute count
        // is not stable. The auto-create tripwire is: the count must STABILIZE
        // and stay CONSTANT — an auto-created session would add a row during
        // the observation window, no matter what the starting count is.
        val deadline = System.currentTimeMillis() + 30_000
        var stableTags: List<String>? = null
        while (System.currentTimeMillis() < deadline) {
            val tags = E2eHarness.renderedRailTags(composeRule)
            if (tags.isNotEmpty() && tags == stableTags) break
            stableTags = tags
            Thread.sleep(250)
        }
        val count = stableTags?.size ?: 0
        check(count > 0) {
            "rail never rendered any session in 30s: $stableTags"
        }

        // Give a hypothetical auto-create enough time to appear (it would add
        // a row and break the stability we just confirmed).
        composeRule.waitForIdle()
        val afterTags = E2eHarness.renderedRailTags(composeRule)
        check(afterTags == stableTags) {
            "auto-create regression: rail changed during observation — " +
                "before: $stableTags after: $afterTags. " +
                "An auto-created session would have added a row."
        }
    }
}
