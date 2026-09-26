package com.m57.hermescontrol.e2e

import com.m57.hermescontrol.data.config.ConnectionProfile
import com.m57.hermescontrol.data.local.AuthManager
import com.m57.hermescontrol.data.ws.HermesWsClient

/**
 * Seeds the app's real auth store so MainActivity lands on ChatScreen
 * (the rail) instead of LandingScreen. androidTest runs in the app
 * process, so AuthManager's public save API is directly callable and
 * tokenFlow reactively re-navigates any already-launched activity.
 */
object E2eHarness {
    /**
     * Reset every piece of process-wide state that the previous test left
     * behind, so scripted reply ids `"1"`/`"2"`/`"3"` and the gate
     * `last_session == null` actually hold for the next test.
     *
     * Must be called BEFORE `seedServerProfile()` (so profile selection is
     * the seed value, not a stale one) and BEFORE `e2eWsOverride = wsUrl`
     * (so the next WS connect consumes the override, not the seeded
     * 127.0.0.1:9119 from a previous test).
     *
     * Two leak vectors if you skip this:
     *  1. `HermesWsClient` is a Kotlin `object`: `requestId`
     *     (process-scope AtomicInteger) survives across tests in the same
     *     instrumentation JVM, so later tests start with id > 1 and
     *     scripted `"1"`/`"2"`/`"3"` envelopes silently miss.
     *  2. `hermes_rail.xml` SharedPreferences (pinned, home_order,
     *     last_session) persists across tests too — a stale `last_session`
     *     routes `handleGatewayReady` into `switchSession(stale_id)`,
     *     which fires an unscripted `session.resume` and an extra
     *     `loadSessions`, so the third scripted envelope (`"3"`) gets
     *     consumed for the wrong RPC.
     */
    fun resetStateForTest() {
        // Full teardown, not just a counter reset: the singleton HermesWsClient
        // survives across tests in the same instrumentation JVM. Clearing only
        // requestId left a live socket + messageQueue + pendingCalls from the
        // prior test, which rerouted/consumed scripted replies out of order.
        // disconnect(clearPendingMessages=true) resets socket, queue, pending
        // calls, generation, status in one call.
        HermesWsClient.disconnect(clearPendingMessages = true)
        HermesWsClient.resetRequestCounterForTest(rejectInflight = true)
        HermesWsClient.e2eWsOverride = null
        // Wipe the per-test rail prefs (pinned, home_order, last_session,
        // icon_recents). Without this, the rail prefs leak across tests in
        // the same instrumentation JVM: a `last_session="stored-current"` set
        // by RenameE2eTest survives into RailReorderE2eTest, routing
        // handleGatewayReady into switchSession(staleId) → which fires an
        // unscripted session.resume before any scripted envelope lands.
        runCatching {
            androidx.test.core.app.ApplicationProvider
                .getApplicationContext<android.content.Context>()
                .getSharedPreferences("hermes_rail", 0)
                .edit()
                .clear()
                .commit()
        }
    }

    fun seedServerProfile(baseUrl: String = "http://127.0.0.1:9119/") {
        // E2E: the test sets HermesWsClient.e2eWsOverride to the MockWebServer
        // URL before launch, so no placeholder is needed here — a placeholder to
        // 9119 caused a doomed first connect (e2e-37 log: ConnectException to
        // ws://127.0.0.1:9119/ws before the real socket opened).
        val profile = ConnectionProfile(
            id = AuthManager.DEFAULT_PROFILE_ID,
            name = "E2E",
            baseUrl = baseUrl,
            // "token", NOT "ticket": a ticket profile puts openSocket into gated mode,
            // which POSTs api/auth/ws-ticket to a server that does not exist in the
            // test env, yielding TRANSIENT_FAILURE and a deferred socket — the WS
            // override is never reached. "token" takes the non-gated path where
            // the offline refresh is best-effort, so the override URL wins.
        )
        AuthManager.saveConnectionProfilesAndSelect(
            profiles = listOf(profile),
            profileId = AuthManager.DEFAULT_PROFILE_ID,
            token = "e2e-token",
        )
        // Force re-publish even if selected id was already the default:
        AuthManager.setSelectedProfileId(AuthManager.DEFAULT_PROFILE_ID)
    }
}
