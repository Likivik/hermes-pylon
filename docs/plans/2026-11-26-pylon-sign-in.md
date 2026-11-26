# Pylon sign-in: one API contract, two login paths

**Goal:** Make Nous sign-in work on Pylon web and in the Pylon Android app, using the gateway's
native bearer flow for the app and the browser cookie for the web, with the same `Authorization`
semantics on the wire.

**Architecture:** The gateway already exposes an RFC 8252 native flow
(`/auth/native/authorize` → loopback redirect → `/auth/native/token` → bearer + refresh) and a
cookie flow (`/auth/login` → provider → `/auth/callback` → `hermes_session_at`). Pylon adds a
`NativeSignIn` seam for the app targets and keeps the existing same-origin proxy for web. No new
gateway endpoints are needed.

**Tech Stack:** Kotlin 2.4.21 / KMP (jvm, android, wasmJs), Ktor client, Compose Multiplatform,
Custom Tabs, `python3 tools/dev-server/serve.py` for the web origin.

---

## 1. What is verified, and from where

Measured against the running gateway (`hermes dashboard` on `127.0.0.1:9119`, version 0.23.1) and
the installed source at
`/nix/store/g0wss07vcg0nnhald112w34hng9zrx9a-hermes-agent-env/lib/python3.14/site-packages/hermes_cli`.

| fact | evidence |
|---|---|
| our gateway advertises `auth_required: true`, `auth_flows: ["cookie","native_pkce"]` | `GET /api/status`, live |
| `GET /auth/native/authorize` takes `provider, code_challenge, code_challenge_method, redirect_uri, state` | `dashboard_auth/routes.py:307` |
| it requires `code_challenge_method=S256` and a non-empty challenge | `routes.py:315-318` |
| the redirect must be `http://` on a loopback IP literal, no fragment, no userinfo, canonical authority | `routes.py:261-296` `_validate_loopback_redirect_uri` |
| `localhost` is rejected in favour of IP literals | same, citing RFC 8252 §8.3 |
| `POST /auth/native/token` takes JSON `{code, code_verifier}` | `routes.py:605` |
| it returns `{access_token, refresh_token, token_type:"Bearer", expires_at, provider, user_id}` and sets **no** cookie | `routes.py:120-125` |
| the code is single-use and expires after **120 s** | `dashboard_auth/native_flow.py:29` |
| `POST /auth/native/refresh` takes JSON `{refresh_token, provider}`; all providers rejecting → `401 session_expired`; unreachable → `503` | `routes.py:626-650` |
| `POST /auth/password-login` returns `{"ok": true, "next": ...}` — **no tokens** | `routes.py:466-508` |
| no endpoint turns a cookie session into a bearer token | full route list: `routes.py:206-626` |
| `POST /api/auth/ws-ticket` mints a single-use WS ticket for an authenticated caller | `routes.py:583` |
| the session cookie is `hermes_session_at` / `_rt` / `_provider`, `HttpOnly`, `SameSite=Lax`, `Path=/` | `dashboard_auth/cookies.py:70-140` |
| `serve.py` passes `Set-Cookie` through (not in `HOP_BY_HOP`) | `tools/dev-server/serve.py:53-62` |

## 2. The correction this plan is built on

An earlier proposal had `serve.py` act as a backend-for-frontend: run the native flow server-side,
hold the bearer token, inject it upstream, and let the browser hold only a Pylon session.

**That cannot work for a phone.** `_validate_loopback_redirect_uri` allows only `127.0.0.1` / `::1`.
For a browser on a phone, `127.0.0.1` is the phone, not the server, so the server-side listener never
receives the redirect. The trick works only when the browser runs on the same host as the listener —
which is not the deployment we have.

**Consequence:** the browser path stays on the cookie. That is not a compromise; a browser should not
hold a bearer token at all (`localStorage` is XSS-reachable, and Pylon already uses it).

## 3. Decisions (dated)

- **DECIDED 2026-11-26:** Web sign-in uses the cookie. Requires the gateway's
  `HERMES_DASHBOARD_PUBLIC_URL` to equal the origin the browser opens. Because Pylon and the gateway
  share a host, `serve.py` on that origin is the answer.
- **DECIDED 2026-11-26:** Android and jvm use the native bearer flow, loopback listener on the device.
- **DECIDED 2026-11-26:** No server-side token custody. Dropped, per §2.
- **DECIDED 2026-11-26:** Token custody per platform: Android `EncryptedSharedPreferences`; jvm a file
  with mode `0600`; wasmJs none (cookie path, unchanged).
- **DECIDED 2026-11-26:** Refresh when `expires_at - now < 120` seconds. On `401 session_expired`,
  clear the store and show the sign-in screen; on `503`, keep the token and surface a retryable error.
- **DECIDED 2026-11-26:** PKCE verifier = 32 random bytes, base64url, no padding (43 chars).
  `state` = 16 random bytes, base64url. Both from a `SecureRandom` actual.
- **DECIDED 2026-11-26:** Loopback listener binds `127.0.0.1` on an ephemeral port, accepts exactly one
  request, and gives up after 60 s. Signed-in result is signalled back to the waiting UI, never polled.

## 4. Scope

In scope: the `NativeSignIn` seam, its two usable actuals, token store, refresh, `AuthManager`
integration, and the web registration step.

Explicitly **not** in scope (YAGNI): multiple users, server-side token storage, iOS, a desktop app
target, custom-scheme redirects (the gateway rejects them; upstream PR #94771 is not in this build).

---

## Phase 1 — Web: make the cookie flow land on Pylon's origin

**Acceptance:** with the browser on the public origin, "Continue with Nous" returns to that origin and
`hermes_session_at` is present; `/api/status` answers 200 with the cookie and 401 without it.

### Task 1: Prove `Set-Cookie` survives the proxy

**Files:**
- Modify: `tools/dev-server/test_serve.py` (new test in `DevServerTest`)
- Test: same file

**Step 1: write the failing test.** `FakeGateway` gains a `/auth/callback` branch that answers `302`
with `Set-Cookie: hermes_session_at=abc; Path=/; HttpOnly; SameSite=Lax`. Then:

```python
def test_set_cookie_crosses_the_proxy_untouched(self):
    status, headers, _ = self.fetch("/auth/callback")
    self.assertEqual(status, 302)
    cookies = headers.get_all("Set-Cookie") or []
    self.assertEqual(len(cookies), 1, "exactly one Set-Cookie must arrive")
    self.assertIn("hermes_session_at=abc", cookies[0])
```

**Step 2: run it.**
`python3 tools/dev-server/test_serve.py`
Expected: FAIL — `/auth/callback` currently reaches the marker reply with no `Set-Cookie`, so
`len(cookies) == 0`.

**Step 3: fix.** No production change should be needed (`HOP_BY_HOP` has no `set-cookie`). If the test
fails, the fault is in `_proxy` at `serve.py:174-201`; the response headers are copied verbatim there,
so the failure would mean a second `Set-Cookie` is being collapsed — reproduce before editing.

**Step 4: run it.** Expected: PASS, and the existing 14 tests still pass.

**Step 5: commit.** `test(dev-server): prove Set-Cookie survives the proxy`

### Task 2: Register the Pylon origin (operator step, no code)

**Files:** none. Recorded here so the step is not folklore.

1. Register `https://<pylon-origin>` at `https://portal.nousresearch.com/local-dashboards` — **origin
   only**, the portal appends `/auth/callback`. Use the MagicDNS hostname, never a raw IP (Safari drops
   PKCE cookies on IP origins).
2. Put the returned client id in the gateway env as `HERMES_DASHBOARD_OAUTH_CLIENT_ID` and set
   `HERMES_DASHBOARD_PUBLIC_URL=https://<pylon-origin>`.
3. Restart the gateway.

**Acceptance:** `curl -s -o /dev/null -D - 'http://127.0.0.1:9119/auth/login?provider=nous'` shows a
`redirect_uri` whose origin equals `<pylon-origin>`. If it still shows the old origin, the restart did
not pick the env up — check `HERMES_DASHBOARD_PUBLIC_URL` is non-blank in the gateway's `.env`.

**Known trade-off:** `HERMES_DASHBOARD_PUBLIC_URL` is one value. Pointing it at the Pylon origin moves
the *dashboard's* SSO to that origin too. The dashboard stays reachable at its own address with password
login. Accept this, or serve both under one origin (a later decision, not this plan).

---

## Phase 2 — App: native bearer flow

**Acceptance:** on an Android device, signing in opens the provider in a Custom Tab, returns to the app,
and every subsequent `/api` call carries `Authorization: Bearer`; the token survives a process restart;
a forced refresh failure returns the user to the sign-in screen instead of a silent 401 storm.

### Task 3: Define the seam

**Files:**
- Create: `ui/src/commonMain/kotlin/com/m57/hermescontrol/ui/authlogin/NativeSignIn.kt`
- Test: `ui/src/commonTest/kotlin/com/m57/hermescontrol/ui/authlogin/NativeSignInTest.kt`

**Step 1: failing test.**

```kotlin
class NativeSignInTest {
    @Test
    fun unsupported_platform_reports_unsupported() {
        assertEquals(SignInSupport.Unsupported, FakeNativeSignIn(available = false).support())
    }
}
```

**Step 2: run.** `./gradlew :ui:jvmTest --tests '*NativeSignInTest*'` → FAIL, unresolved reference.

**Step 3: implement** the interface plus a `NativeToken` value type:

```kotlin
data class NativeToken(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSeconds: Long,
    val provider: String,
)

sealed interface SignInSupport { data object Supported : SignInSupport; data object Unsupported : SignInSupport }

interface NativeSignIn {
    suspend fun support(): SignInSupport
    suspend fun signIn(gatewayBaseUrl: String, provider: String): NativeToken
}
```

**Step 4: run.** PASS.

**Step 5: commit.** `feat(auth): add the native sign-in seam`

### Task 4: PKCE generation

**Files:**
- Create: `ui/src/commonMain/.../ui/authlogin/Pkce.kt`
- Test: `ui/src/commonTest/.../PkceTest.kt`

**Failing test:** verifier is 43 chars of `[A-Za-z0-9_-]`, two calls differ, and the challenge is the
base64url SHA-256 of the verifier with no padding. Use the RFC 7636 Appendix B vector as the fixed
case rather than a self-computed expectation.

**Implementation:** `expect fun randomBytes(n: Int): ByteArray` plus SHA-256. The jvm/android actuals
use `SecureRandom` and `java.security.MessageDigest`; wasmJs uses `crypto.getRandomValues` and
`crypto.subtle.digest`. **Both actuals must be implemented in this task** — a `commonMain` declaration
with one missing actual does not compile for the wasm target, which is a gate task.

**Acceptance:** `:ui:jvmTest` and `:ui:compileKotlinWasmJs` both pass.

### Task 5: Authorize URL builder

**Files:** create `ui/src/commonMain/.../ui/authlogin/AuthorizeUrl.kt`; test alongside.

**Failing test:** the built URL has `code_challenge_method=S256`, a `redirect_uri` matching
`^http://127\.0\.0\.1:\d+/callback$`, and the exact `state` passed in. Encode with `%3A%2F%2F` for the
scheme separator, never a raw `://` in a query value.

**Why loopback and not `localhost`:** the gateway rejects `localhost` outright
(`_validate_loopback_redirect_uri`, RFC 8252 §8.3). A test asserting `localhost` is rejected locally
prevents a future "cleanup" that would break against the real gateway.

### Task 6: Loopback listener

**Files:** `ui/src/commonMain/.../ui/authlogin/LoopbackReceiver.kt` (expect) with actuals in
`androidMain` and `jvmMain`; test in `jvmTest` against a real socket.

**Failing tests, all four:**
1. a request with `?code=X&state=<expected>` yields `code == "X"`;
2. a request with a different `state` is rejected and the listener keeps waiting;
3. no request within the timeout yields `native timeout` in under 61 s (inject the timeout; do not sleep);
4. the socket is closed after one accepted callback — a second connect is refused.

**Implementation:** `ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))`, read one request line,
parse the query with `java.net.URI`, answer `200` with a one-line HTML page, close in `finally`.

**Acceptance:** all four pass, and no test leaves a listening socket behind (`ss -ltnp` shows none after).

### Task 7: Token exchange

**Files:** `ui/src/commonMain/.../ui/authlogin/TokenExchange.kt`; test in `commonTest` with a MockEngine.

**Failing tests:**
- posts to `<base>/auth/native/token` with JSON body `{"code": ..., "code_verifier": ...}`;
- parses `access_token`, `refresh_token`, `expires_at`, `provider` into `NativeToken`;
- a `400` raises `SignInFailed.InvalidCode`, not a generic exception;
- the request must **not** send a `Cookie` header.

**Implementation:** reuse the existing `HermesHttpClient`; do not create a second Ktor client (a client
built on the shared OkHttp client must never be closed — see `references/ktor-transport.md`).

### Task 8: Token store

**Files:** `ui/src/commonMain/.../data/local/NativeTokenStore.kt` (expect) and three actuals; test in
`commonTest` against a fake.

**Failing tests:** save → load returns an equal value; clear → load returns null; a stored token whose
`expiresAtEpochSeconds` is in the past still loads (expiry is the caller's decision, not the store's).

**Actuals:** Android `EncryptedSharedPreferences`; jvm a file written `0600` via `Files.setPosixFilePermissions`
inside an atomic temp-file rename; wasmJs returns `null` from load and no-ops on save (cookie path).

**Dependency check before starting:** confirm `androidx.security:security-crypto` (or the modern
equivalent) is in `gradle/libs.versions.toml`. If it is not, adding it is its own commit and must not be
bundled with logic — a dependency bump and a feature in one commit is unreviewable.

### Task 9: Refresh

**Files:** `ui/src/commonMain/.../ui/authlogin/TokenRefresh.kt`; test with MockEngine.

**Failing tests:**
- `expires_at - now < 120` triggers exactly one refresh;
- two concurrent callers trigger **one** refresh, not two (single-flight; assert the engine saw one request);
- `401 session_expired` clears the store and returns `SignedOut`;
- `503` returns `Retryable` and **keeps** the token;
- the refreshed payload replaces the stored pair.

### Task 10: `AuthManager` integration

**Files:**
- Modify: `ui/src/commonMain/kotlin/com/m57/hermescontrol/data/local/AuthManager.kt`
- Test: `app/src/test/java/com/m57/hermescontrol/data/local/AuthManagerTest.kt`

**Failing tests:**
- when a `NativeToken` is present, `/api/*` requests carry `Authorization: Bearer <token>` and **no** cookie;
- when none is present, behaviour is unchanged (password path still sends the cookie);
- the WS ticket call succeeds on the bearer path and the socket then connects;
- `signOut()` clears the store **and** posts `/auth/logout`.

**Why the WS ticket matters:** a bearer-only client that forgets `api/auth/ws-ticket` gets a REST API
that works and a socket that never opens — the failure shows as "Reconnecting…" with no HTTP error
(the class of bug recorded in the pylon skill). The test above exists to catch exactly that.

### Task 11: Platform availability gate

**Files:** `ui/src/commonMain/.../ui/authlogin/NativeSignInGate.kt`; test in `commonTest`.

**Failing test:** given `/api/status` answering `auth_flows: ["cookie"]`, the UI offers password login
only; given `["cookie","native_pkce"]`, it offers "Continue with Nous".

**Implementation:** read `auth_flows` once at connect time and cache it in the connection profile. On
`Unsupported` from the seam, fall back to password — never show a button that cannot work.

**Acceptance:** on this machine the gate selects `native_pkce` (our gateway advertises it); a profile
pointed at an older gateway selects password.

---

## 5. Files likely to change

```
ui/src/commonMain/.../ui/authlogin/NativeSignIn.kt          new
ui/src/commonMain/.../ui/authlogin/Pkce.kt                  new
ui/src/commonMain/.../ui/authlogin/AuthorizeUrl.kt          new
ui/src/commonMain/.../ui/authlogin/LoopbackReceiver.kt      new (expect)
ui/src/commonMain/.../ui/authlogin/TokenExchange.kt         new
ui/src/commonMain/.../ui/authlogin/TokenRefresh.kt          new
ui/src/commonMain/.../ui/authlogin/NativeSignInGate.kt      new
ui/src/commonMain/.../data/local/NativeTokenStore.kt        new (expect)
ui/src/androidMain/.../ui/authlogin/*Actual.kt              new
ui/src/jvmMain/.../ui/authlogin/*Actual.kt                  new
ui/src/wasmJsMain/.../ui/authlogin/*Actual.kt               new
ui/src/commonMain/.../data/local/AuthManager.kt             modify
tools/dev-server/test_serve.py                              modify (one test)
gradle/libs.versions.toml                                   maybe (security-crypto)
```

## 6. Gate

```
python3 scripts/check_kmp_boundary.py                        # must print nothing
/var/lib/hermes/bin/pylon-build.sh ktlintCheck                # separate invocation
/var/lib/hermes/bin/pylon-build.sh :shared:jvmTest :ui:jvmTest :app:testDebugUnitTest
/var/lib/hermes/bin/pylon-build.sh :ui:compileKotlinWasmJs
python3 tools/dev-server/test_serve.py                        # 15 tests
```

Exit 75 means the build lock is held — wait, never force.

## 7. Risks

- **`expires_at` units.** The gateway returns `session.expires_at`; confirm it is epoch **seconds** with
  one live probe before Task 9 ships. A millisecond value makes every token look expired and produces a
  refresh on every call.
- **`webapp_sign_in.token_works_alone`.** Refresh only rotates tokens a native sign-in produced. A token
  minted by the cookie flow and then used as a refresh token gets `401 session_expired`. Keep the two
  paths separate; do not migrate a cookie session into the store.
- **Custom Tabs vs WebView.** Use `CustomTabsIntent`. A WebView shares the app's cookie jar and defeats
  the purpose of the flow.
- **120 s code TTL.** The code is minted *after* the provider login, so a slow login does not expire it.
  Do not add a retry that reuses a consumed code — it is single-use by design.
- **The web trade-off is real.** Moving `HERMES_DASHBOARD_PUBLIC_URL` to the Pylon origin moves the
  dashboard's SSO with it. Recorded in Task 2; not solved by this plan.

## 8. Not doing

- Custom-scheme redirects. The gateway returns `400` for any non-`http` loopback scheme; upstream PR
  #94771 is not in this build. Revisit when it ships, for iOS.
- A second gateway process for a second OAuth client.
- Server-side token custody for web (see §2).
