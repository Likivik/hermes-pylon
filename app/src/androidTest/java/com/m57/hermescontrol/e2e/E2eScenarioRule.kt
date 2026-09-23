package com.m57.hermescontrol.e2e

import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.m57.hermescontrol.MainActivity
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * E2E activity rule that seeds
 * [com.m57.hermescontrol.data.local.AuthManager] BEFORE launching
 * [MainActivity], so the activity's very first composition already lands on
 * ChatScreen (with the rail) instead of LandingScreen.
 *
 * Why this exists
 * ----------------
 * `createAndroidComposeRule<MainActivity>()` wraps an
 * `ActivityScenarioRule<MainActivity>` whose `before()` launches the activity
 * inside the Statement that JUnit runs before the test class's `@Before`
 * methods. So when MainActivity reaches onCreate, AuthManager's store is
 * empty: no profile, no token, the gate predicate in
 * `Navigation.authenticatedStartScreen` resolves to `LandingScreen`, and
 * ChatScreen / the rail are never composed by the first frame.
 *
 * The previous e2e tests tried to work around this by calling
 * `E2eHarness.seedServerProfile()` from inside the test body. The flow was:
 * MainActivity launches → lands on LandingScreen → recomposes to ChatScreen
 * once `tokenFlow` publishes. Under a warm emulator that works. Under Gradle
 * Managed Devices (cold-boot Pixel 5, swiftshader GPU, API 34) the Compose UI
 * test rule's `waitUntilAtLeastOneExists` polls the semantics tree every
 * frame; the activity's first compose already produced a node hierarchy, but
 * the rule's polling hits a transient "Activity top resumed state loss
 * timeout" / "Activity pause timeout" window that the system reports in
 * device logcat. While in that window `getAllSemanticsNodes(
 * atLeastOneRootRequired = true)` returns empty and the rule raises
 * `IllegalStateException: No compose hierarchies found in the app.` — the
 * failure we saw on e2e-17/18/19.
 *
 * `ActivityScenarioRule` is declared `final`, so we can't subclass it.
 * Instead we wrap it in a `TestRule` that runs the seed inside our
 * `evaluate()` **before** we delegate to the wrapped rule's `apply()`. The
 * wrapper's `apply()` is invoked by the JUnit framework, and our
 * `Statement.evaluate()` runs to completion (seed + delegate lifecycle)
 * before JUnit returns control to the test runner — so by the time
 * `AndroidComposeTestRule`'s `activityProvider` runs, the seed has already
 * published the token and MainActivity's first composition routes straight
 * to ChatScreen.
 *
 * Lifecycle
 * ---------
 * 1. Wrapper's `evaluate()`: `seed()` runs (AuthManager.profile + token
 *    published into the StateFlows).
 * 2. We then call `delegate.apply(base, description).evaluate()` which is
 *    `ExternalResource`'s lifecycle: `before()` launches MainActivity →
 *    `base.evaluate()` runs the test body → `after()` closes the scenario.
 * 3. MainActivity.onCreate: `tokenFlow.collectAsState()` reads the freshly
 *    published `"e2e-token"`, `authenticatedStartScreen` resolves to
 *    ChatScreen; the very first composition lays out ChatScreen and the
 *    SessionRail's `rail_item_*` testTags are wired (they render once the WS
 *    session.list ack arrives).
 *
 * `HermesWsClient.e2eWsOverride` stays in the test body, because the mock's
 * port is allocated at runtime by `ScriptedGatewayServer.start()`. The
 * HermesWsClient backoff (1 s initial, 30 s max) picks up the override on
 * its next retry after the test body sets it; `gatewayServer.awaitOpen()`
 * waits up to 30 s, which comfortably covers the first retry.
 */
class E2eScenarioRule(
    private val seed: () -> Unit = { E2eHarness.seedServerProfile() },
) : TestRule {
    /**
     * Wrapped rule that owns the `ActivityScenario` lifecycle. Cannot be
     * subclassed (declared `final`), so we hold it as a delegate and expose
     * [scenario] / [activity] for `AndroidComposeTestRule.activityProvider`.
     */
    private val delegate: ActivityScenarioRule<MainActivity> =
        ActivityScenarioRule(MainActivity::class.java)

    /** Exposed for `AndroidComposeTestRule.activityProvider`. */
    val scenario: androidx.test.core.app.ActivityScenario<MainActivity> get() = delegate.scenario

    override fun apply(base: Statement, description: Description): Statement =
        object : Statement() {
            override fun evaluate() {
                // Seed BEFORE the delegate launches the activity. Both the
                // Compose test environment and our wrapper run inside
                // AndroidComposeTestRule's `environment.runTest { ... }`
                // closure, so by the time the wrapped statement reaches
                // `super.before()` (which calls `ActivityScenario.launch`),
                // the tokenFlow has already published.
                seed()
                delegate.apply(base, description).evaluate()
            }
        }

    /**
     * Synchronous activity accessor. `ActivityScenario.onActivity` blocks the
     * caller until the activity is resumed, so this is safe to call from
     * inside `AndroidComposeTestRule.activityProvider` after `delegate.apply`
     * has reached `before()`.
     */
    fun activity(): MainActivity {
        var resolved: MainActivity? = null
        scenario.onActivity { resolved = it }
        return checkNotNull(resolved) {
            "Activity was not set on the scenario; rule is not active"
        }
    }
}