package com.m57.hermescontrol.e2e

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeTestRule
import com.m57.hermescontrol.data.config.ConnectionProfile
import com.m57.hermescontrol.data.local.AuthManager
import com.m57.hermescontrol.data.remote.AuthPayloads
import com.m57.hermescontrol.data.remote.OkHttpProvider
import com.m57.hermescontrol.data.ws.HermesWsClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

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

    /**
     * Read the ephemeral dashboard password from the test-runner args.
     * CI passes it via `-Pandroid.testInstrumentationRunnerArguments.e2ePassword=...`.
     * Returns `null` if the arg is absent or blank — callers should fail with a
     * clear message rather than silently using an empty password.
     */
    fun realGatewayPasswordFromArgs(): String? {
        val args = androidx.test.platform.app.InstrumentationRegistry.getArguments()
        val pw = args.getString("e2ePassword")?.takeIf { it.isNotBlank() }
        return pw
    }

    /**
     * Seed the profile against the REAL gateway (the workflow-started
     * `hermes dashboard` on 127.0.0.1:8642) using the ephemeral CI password.
     *
     * Auth flow (mirrors what AuthLoginViewModel.connectBasicAuth does):
     *  1. POST `/auth/password-login` with `{provider:"basic", username, password, next:""}`
     *     via the shared `OkHttpProvider.probe` client. The probe carries the
     *     `PersistentCookieJar`, so the Set-Cookie response lands in the
     *     active serverId scope (set by `saveConnectionProfilesAndSelect`).
     *  2. Save the profile with `baseUrl="http://127.0.0.1:8642/"` and set
     *     `wsAuthParam="ticket"` on the SELECTED profile, putting
     *     `AuthManager.isGatedMode()` into true. Subsequent
     *     `HermesWsClient.openSocket()` mints a ws-ticket via
     *     `POST /api/auth/ws-ticket` (carrying the session cookie from step 1).
     *     3. Set `HermesWsClient.e2eWsOverride = baseUrl + "/api/ws"`. The override
         *        is a BASE url — `openSocket()` appends `?ticket=<minted>` when a
         *        ticket was just minted in gated mode, so the real `/api/ws` rejects
         *        unauthenticated handshakes.
     */
    fun seedRealGatewayProfile(
        baseUrl: String = "http://10.0.2.2:8642/",
        password: String,
    ) {
        require(password.isNotBlank()) {
            "seedRealGatewayProfile requires a non-blank password (the CI ephemeral password)"
        }
        // Pin the cookie scope to the default profile id BEFORE the login so
        // the Set-Cookie response lands in the same scope the selected profile
        // will own after saveConnectionProfilesAndSelect(). Without this, a
        // previous real-gateway test that switched the scope elsewhere would
        // leave the cookies stranded in that other scope and the ticket mint
        // would miss them.
        com.m57.hermescontrol.data.remote.CookieManager.useStore(AuthManager.DEFAULT_PROFILE_ID)
        com.m57.hermescontrol.data.remote.CookieManager.beginAuthentication()

        val passwordJson = AuthPayloads.passwordLogin(username = "admin", password = password)
        val loginMediaType = "application/json; charset=utf-8".toMediaType()
        val loginBody = passwordJson.toRequestBody(loginMediaType)

        // Build the request against the probe so CookieJar captures Set-Cookie.
        // We pinned the cookie scope to DEFAULT_PROFILE_ID and called
        // beginAuthentication() above, mirroring AuthLoginViewModel — the
        // pinned scope means the cookies land in the same scope the selected
        // profile will own after saveConnectionProfilesAndSelect().
        val loginRequest =
            Request
                .Builder()
                .url(baseUrl.trimEnd('/') + "/auth/password-login")
                .header("Content-Type", "application/json")
                .post(loginBody)
                .build()

        val loginResponse =
            try {
                OkHttpProvider.probe.newCall(loginRequest).execute()
            } catch (t: Throwable) {
                throw IllegalStateException(
                    "seedRealGatewayProfile: password-login HTTP call failed against $baseUrl — " +
                        "is the real gateway up and basic-auth wired? ${t.message}",
                    t,
                )
            }
        loginResponse.use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException(
                    "seedRealGatewayProfile: password-login returned HTTP ${resp.code} for " +
                        "admin@$baseUrl. Wrong username/password? Wrong provider? " +
                        "Expected {provider:\"basic\", username:\"admin\", password, next:\"\"}.",
                )
            }
            // The session cookies (hermes_session_at / hermes_session_rt) are
            // persisted by PersistentCookieJar.saveAll() — OkHttp calls it on
            // every response, including ours. They live in the active serverId
            // scope (the "default" profile, set in saveConnectionProfilesAndSelect).
        }

        val profile =
            ConnectionProfile(
                id = AuthManager.DEFAULT_PROFILE_ID,
                name = "E2E-REAL",
                baseUrl = baseUrl,
                // ticket mode: real ws-ticket flow against the real gateway.
                wsAuthParam = "ticket",
            )
        AuthManager.saveConnectionProfilesAndSelect(
            profiles = listOf(profile),
            profileId = AuthManager.DEFAULT_PROFILE_ID,
            token = "e2e-token", // placeholder; real auth via ticket
        )
        // Belt-and-suspenders: in case the profile persisted a previous
        // wsAuthParam, force it to "ticket" on the selected one.
        AuthManager.setWsAuthParam("ticket")
        AuthManager.setSelectedProfileId(AuthManager.DEFAULT_PROFILE_ID)
        // Point the WS client at the real /api/ws endpoint of the gateway.
        // openSocket() appends ?ticket=<minted> in gated mode.
        // 10.0.2.2 = emulator alias for the host loopback (gateway hosts on
        // the CI runner). ws:// scheme for the WS upgrade.
        HermesWsClient.e2eWsOverride =
            baseUrl.trimEnd('/').replaceFirst("http://", "ws://") + "/api/ws"
    }

    /**
     * Wait (up to [timeoutMillis]) for at least one rail item to render and
     * return its UI tag (`rail_item_<id>`). Matches by tag PREFIX so tests
     * never parse/depend on RPC id fields — session.list's `id` is the only
     * id the rail tags by (stored_session_id ≠ list id, e2e-69 lesson).
     */
    fun waitForFirstRailItem(
        composeRule: ComposeTestRule,
        timeoutMillis: Long = 30_000,
    ): String {
        val railItemMatcher = SemanticsMatcher("rail item") { node ->
            node.config.getOrNull(SemanticsProperties.TestTag)
                ?.startsWith("rail_item_") == true
        }
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodes(railItemMatcher).fetchSemanticsNodes().isNotEmpty()
        }
        return composeRule.onAllNodes(railItemMatcher)
            .fetchSemanticsNodes()
            .firstOrNull()
            ?.config?.getOrNull(SemanticsProperties.TestTag)
            ?: error("rail item rendered but no tag found")
    }
}