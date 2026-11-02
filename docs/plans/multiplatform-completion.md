# Multiplatform Completion — Implementation Plan

> **For Hermes:** execute task-by-task under the §4 Delegation Contract. One PR per phase, one concern per PR.

**Goal:** the client runs as the same application on Android, desktop JVM and wasmJs — every screen mounted, every internal out of `androidMain` except what is genuinely Android-bound (Room, Keystore, notifications).

**Architecture:** one shared UI (`:ui/commonMain`), one platform seam per *capability* (`AuthPlatform`, `SecretStore`, `CookiePlatform`, `FilesPlatform`, `ExternalUrlLauncher`, `AnalyticsCacheStore`, log seams), `:shared` holding the pure domain, and one common screen graph with per-target capability flags.

**Tech stack:** Kotlin 2.4.21 · Compose Multiplatform 1.12.2 · AGP 9.1.1 · Ktor 3.6 (CIO, no OkHttp) · DataStore 1.3 + okio · kotlinx-atomicfu · Room 2.7 (Android only) · Navigation 3.

---

## 1. Verified current state

Measured in-session, not estimated.

| Area | Measurement |
|---|---|
| `:ui/commonMain` | 201 files — all screens + view models |
| `:ui/androidMain` | 75 files / 6,244 lines |
| `:ui/jvmMain` · `:ui/wasmJsMain` | 37 · 40 files |
| Files with **no** platform API at all | 28 files / 1,786 lines (upper bound — imports + type names only) |
| Stubs (`UnsupportedOperationException`/`TODO`) | wasm 2 (`FilesPlatformWasm`, `SecureRandomBytes`), jvm 0 |
| `screenGraph()` | `expect` at `ScreenGraph.kt:78`; Android mounts ~18 `ScreenDefinition`s, jvm + wasm return `screens = emptyList()` |
| Boundary checker | 0 violations in both modules, 0 expects without actuals, 13/13 tests |
| Test baseline | `:app` 104 suites / 1,215 tests · `:shared` 13 / 216 |

Largest remaining `androidMain` files: `GatewayFileClient` 362 · `Navigation.kt` 310 · `HubSkillsTab` 302 · `PersistentCookieJar` 297 · `ProcessesScreen` 273 · `SecureStorage` 254 · `ScreenRegistry` 232 · `MediaPlaybackAndroid` 205 · `SeekableGatewayMediaSession` 195 · `SecureBlobStore` 192 · `ImageBytesResolver` 184 · `CookieManager` 149 · `HermesDatabase` 143.

## 2. Architecture assessment

**Sound.** One interface per capability (7 seams, 3 targets) is the right granularity; `:shared` purity and the `:ui` KMP allow-list are machine-checked; DataStore + okio already prove one storage abstraction across targets; atomicfu removed the last JVM-only constructs from common code.

**Three weaknesses, one per phase:**

1. **`screenGraph()` per target duplicates the inventory** and has already diverged (18 vs 0). → one common graph + `Set<Capability>` flags (Phase 1).
2. **Room is a silent Android hole.** Room stays Android-only; jvm/wasm need the same `ChatPersistence` interface so "no history in the browser" is a decision, not an accident (Phase 5).
3. **Web security unspecified.** No `SecretStore` web actual; `secureRandomBytes` throws on wasm. → layered policy, §8 (Phase 4).

**Rejected alternatives (recorded so they are not re-litigated):**
- *Room on desktop* — Room 2.7 runs on JVM, but wasm cannot, so the browser needs an interface implementation anyway; two persistence stacks for zero users is the worse design.
- *Per-target screen lists* — every new screen costs N edits and drifts again.
- *Google `androidx.navigation3.ui` on common* — Android-only artifact; the JetBrains KMP build is the same API (verified, Phase 1).

## 3. Phase 0 — DONE

**T0.1 ✅ (restored)** `scripts/check_kmp_boundary.py` scans `commonMain` for JVM-only constructs (`synchronized(`, `Thread.`, `System.`, `String.format`, `Locale.`, `javaClass`, `runBlocking`, `Dispatchers.IO`), excusing a construct when the file imports the multiplatform symbol that legalises it (`kotlinx.atomicfu.locks.synchronized`). **This work had never reached `main`** — the checker on `main` had import and expect/actual checks only, which is how `runBlocking` passed local review and failed CI in PR #91's predecessor. Restored with 6 tests; 16 pass; checker reports 0 constructs across both modules.

**T0.2 ✅ documented, runtime pending** CMP 1.12.2's compose view provides a `ViewModelStoreOwner` (JetBrains PR #1291, merged), and CMP for web fully supports the common Navigation APIs including Back/Forward. Runtime confirmation lands at T1.5, where the browser build happens anyway.

---

## 4. Delegation Contract (anti-runaway)

A three-paragraph brief once ran 52 min, died at 60 %, and needed three follow-up PRs; an equivalent numbered checklist finished in 13 min, correct first try. These rules encode that.

**Scope caps**
- ≤ **3 files** and ≤ **200 changed lines** per task. More → split the task, don't push through.
- Tasks list **exact paths**. Needing a 4th file → stop and report, do not widen.
- No refactors outside listed files, no "while I'm here" fixes, no test rewrites except those the task names.

**Time caps**
- Parent sets `child_timeout_seconds = 1800` for implementation, `900` for research.
- A task whose honest estimate exceeds 30 min is mis-scoped — split it.
- Parent checks `delegate_task action=list` at the halfway mark. At **2× the estimate**: `action=stop`, finish in-session. Partial output beats a dead child; a child that has produced nothing by then was mis-briefed.

**Build rules (memory + lock)**
- Children **never** run Gradle. Compilation and tests are CI's job.
- Allowed in a child: file reads, `python3 scripts/check_kmp_boundary.py`, `python3 scripts/test_check_kmp_boundary.py`, grep-class inspection.
- Parent-only local builds go through `/var/lib/hermes/bin/pylon-build.sh` (exit 75 = busy → sleep and retry; exit 76 = not enough RAM → free memory, never override to force).

**Report format (≤20 lines)**
```
TASK: <id>   STATUS: done | blocked | partial
FILES: <path (lines changed)> …
VERIFIED: <exact command + result actually observed>
NOT DONE: <anything skipped, stated plainly>
BLOCKER: <one line, or "none">
```

**Evidence rule**
- A child's "done" is a claim. The parent verifies every deliverable itself — read the diff, run the checker, watch CI — before merge.
- "It compiles" without the command and exit code is unverified.
- A child claiming a run it could not have made has its task redone by the parent.

**Merge rules**
- One concern per PR; body names deleted tests and behaviour changes explicitly.
- Merge only on four green checks (Merge Conflict Detector, CodeQL, Unit tests, Android CI/CD). Docs-only included — waiting costs nothing.
- Never push upstream; only `Likivik/hermes-pylon`.

---

## 5. Phase 1 — Navigation into `commonMain` (the visible win)

**Verified before scoping:**

| Claim | Status |
|---|---|
| `Navigation.kt` portable — 310 lines, no `android.*`/`Context`/`Intent`/`BackHandler` | ✅ grepped twice |
| `androidx.navigation3.runtime` already in `commonMain` (`ScreenGraph.kt:4`, `NavigationController.kt:8-9`, `NavigationKeys.kt:3`), compiling for wasmJs | ✅ |
| `viewModel()` used 28× in `commonMain` | ✅ |
| Source sets: `commonMain` 194, `jvmMain` 285, `androidMain` 299 — Google nav3 coords at 340–342 are androidMain-only | ✅ |
| JB `navigation3-ui:1.1.0` cached with `-wasm-js` klib **and** `-desktop` jar | ✅ |
| JB 1.1.0 `NavDisplay` exposes **both** `(backStack, …, entryProvider)` and `(sceneState, …)` | ✅ byte-level check — **no call-site rewrite** |
| `AndroidChatScreen` is a 45-line `LocalContext` + `androidChatEnvironment` adapter | ✅ |

**T1.3 → run FIRST: chat view-model factory seam.**
Files: `ui/src/androidMain/.../ui/chat/AndroidChatScreen.kt` (replace), `ui/src/commonMain/.../ui/chat/ChatViewModelFactory.kt` (new, ≤25 lines), one actual per non-Android target.
Why first: `ScreenRegistry.kt:70` mounts chat as `{ sessionId, openDrawer -> AndroidChatScreen(…) }`. Chat is primary — it cannot be capability-excluded, so the graph cannot move before this does.
Steps: read the wrapper + both `Main.kt`s · `expect fun rememberChatViewModel(sessionId: String?, resolver: Resolver): ChatViewModel` · Android actual keeps `androidChatEnvironment(context, …)` · jvm/wasm mirror their entry code · delete the wrapper.
Verify (CI): `:app:compileDebugKotlin`, `:ui:compileKotlinJvm`, `:ui:compileKotlinWasmJs` + checker 0/0.
Stop-if: a 4th file is required, or chat construction turns out to depend on an Android-only service (report which).

**T1.1 — one common screen graph with capability flags.**
Files: `ScreenGraph.kt` (common), the three `ScreenRegistry*/ScreenGraphActual` files.
Steps: move graph assembly to `commonMain` · add `requires: Set<Capability>` to `ScreenDefinition` · one `fun platformCapabilities(): Set<Capability>` per target · filter at construction · delete `ScreenGraphActual.kt` ×3.
Verify: three compiles + checker; Android drawer lists the same screens (screenshot T1.5).
Stop-if: a screen's `content` needs a `Context` after T1.3 — that is one more seam, report it.

**T1.2 — move `Navigation.kt`, swap the nav3 coordinate.**
Files: `Navigation.kt` (move), `ui/build.gradle.kts`.
Steps: **replace** — not add — `api(libs.androidx.navigation3.ui)` + `api(libs.androidx.navigation3.runtime)` in the androidMain block with the JB `org.jetbrains.androidx.navigation3:navigation3-ui` + `-runtime` coordinates on `commonMain`, pinned **1.1.0**. Reconcile `lifecycle-viewmodel-navigation3` the same way (JB coordinate already at `libs.versions.toml:90`). Import lines unchanged.
Why swap: two artifacts with the same packages is a duplicate-class dex failure, not a warning.
Verify: three compiles + `:app:assembleDebug` (dex merge) + `NavigationController` tests.

**T1.4 — point the browser entry at the app.**
Files: `ui/src/wasmJsMain/.../ui/Main.kt`.
Steps: keep `ComposeViewport` / `onWasmReady` / CSS-injection boilerplate (~48–101) verbatim; replace the landing body with the real nav host.
Verify: `:ui:compileDevelopmentExecutableKotlinWasmJs` in CI.

**T1.5 — see it work.** 
**RESULT (partial):** the dev bundle builds (`:ui:wasmJsBrowserDevelopmentWebpack` rc=0, `ui.js` 3.6 MB + 47 MB wasm) and is served over the tailnet at `https://serenity.oryx-galaxy.ts.net:8445/` (port 8445 → 8080 → `scratch/pylon-web`), correct MIME for html/js/wasm. **Screenshots not done: this host has no Chrome/Chromium binary**, so the automation browser cannot start. Either install chromium via nix or verify by hand on a phone. T0.2's runtime confirmation is still outstanding for the same reason.

---

## 6. Phase 2 — the 28 portable leftovers (upper bound 1,786 lines)

**T2.1 batch A — RESULT (six candidates tried, one moved).**

| Candidate | Outcome | Blocked by |
|---|---|---|
| `ui/skills/components/HubSkillsTab.kt` (302) | **moved** (#88) | — |
| `data/remote/GatewayTransport.kt` (137) | reverted | `CookieManager` (same package, no import) |
| `data/remote/CookieSerialization.kt` (121) | reverted | `COOKIE_MAX_DATE_MILLIS` |
| `data/local/ChatMessageMapper.kt` (63) | reverted | `ChatMessageEntity` (Room) |
| `data/remote/CookieMatching.kt` (53) | reverted | `CookieHolder` |
| `data/remote/DashboardSessionTokenRefresher.kt` (67) | reverted | `runBlocking`, `Synchronized` — compiles on JVM, fails on wasm |

The static scan's predicate — "this file names no platform API" — cannot see a **same-package** reference to an `androidMain` symbol: there is no import to match and no `android.` token to find. In a single-package module split across source sets this is the normal case. So 1,232 "portable" lines is an upper bound that overcounts heavily; the surviving mass is far smaller and most of the rest is Phase 3 work in disguise (`CookieHolder`/`CookieManager` → cookie port, `ChatMessageEntity` → Room boundary).

**Method per batch (revised):** the scan only nominates. Batch 3–4 candidates → `:ui:compileKotlinJvm` **and `:ui:compileKotlinWasmJs`** → revert failures, keep passes.

A JVM/Android compile is *not* a portability check: `runBlocking` and `kotlin.jvm.Synchronized` compile cleanly on JVM and only break in the wasm compile, which is why `DashboardSessionTokenRefresher` passed locally and failed CI. Pre-push for any `commonMain` change: `python3 scripts/check_kmp_boundary.py` (0/0) **and** `:ui:compileKotlinWasmJs`.
**T2.2 batch B (≤3 files):** remaining `ui/common/*`.
**T2.3 batch C (≤3 files):** `util/*` (6 files → two tasks), verifying each against the checker before moving.
**T2.4 reconcile the count:** re-run the scan after Phase 1 — T1.1/T1.3 may already have moved some — and put the new number in the PR body. 28 is an upper bound, not a target.
**T2.5 delete empty packages** left by these moves only; no opportunistic deletions.
Method per batch: move → push → CI compile → next batch. Never five files then one build.
Verify: CI compiles + checker 0/0.
Stop-if: a file needs a new seam to move (that is a seam task, not a move task).

## 7. Phase 3 — media and files (chat usable on web)

**T3.1 `MediaPlayback` seam.** Files: `MediaPlaybackAndroid.kt` (205), new common interface, wasm + jvm actuals. JVM ships `canPlay = false` in this phase, stated in the PR; web uses an HTML media element.
**T3.2 `ImageBytesResolver` + decode seam.** File: `ImageBytesResolver.kt` (184) → common fetch (Ktor handles HTTP since #66) + `expect` decode; web uses blob URLs. `MAX_IN_MEMORY_MEDIA_BYTES` unchanged.
**T3.3 okio for file + range access.** Files: `GatewayFileClient.kt` (362), `SeekableGatewayMediaSession.kt` (195) → okio `FileSystem`; web gets an in-memory `FileSystem`. Range semantics (206/`Content-Range`) untouched; `GatewayFileClientTest` unchanged and green.
**T3.4 cookie persistence per target.** Files: `PersistentCookieJar.kt` (297), `CookieManager.kt` (149). Web: `localStorage` jar; jvm: file; Keystore encryption stays Android's answer.
Verify per task: CI compiles; the existing media/file tests unchanged.

## 8. Phase 4 — secrets per target (policy closed, §13)

**T4.1 `SecretStore` web actual.** WebCrypto AES-GCM, **non-extractable** key in IndexedDB, `iv‖ciphertext` in `localStorage` under the existing `auth/` namespace (`authSecretKey`), `deletePrefix` = enumerate-and-remove.
**T4.2 `secureRandomBytes` wasm actual.** Replace the throw with `crypto.getRandomValues`; a wasm test asserts length and non-repetition.
**T4.3 "remember on this device" toggle.** Default stays session-only (`InMemoryAuthStore`); toggle default OFF, one sentence of consequence in the UI and in SECURITY.md.
Verify: round-trip test in the wasm test source set; `auth/` values never appear in plaintext.

## 9. Phase 5 — Room boundary

**T5.1 `ChatPersistence` for jvm/wasm.** In-memory (wasm) / file (jvm) behind the existing interface. Room stays in `androidMain`; no DAO type in `commonMain` (the checker's `androidx.room` exclusion enforces it).
**T5.2 state the consequence.** Docs + PR body: no persisted chat history in the browser — a product decision, not a missing feature.

## 10. Phase 6 — CI, docs, preview

**T6.1** `:ui:jvmTest` in `.github/workflows/unit.yml` (the wasm executable compile already exists in `android.yml`; do not duplicate it).
**T6.2** `docs/kmp-boundary.md`: expect table + capability matrix. `AGENTS.md`: capability matrix and "what each target can't do".
**T6.3** Preview refreshes on every phase that touches the browser build; the command lives in §11.

---

## 11. Verification matrix

| Check | When | Expected |
|---|---|---|
| `python3 scripts/check_kmp_boundary.py` | every PR touching `commonMain` | 0 violations, both modules |
| `python3 scripts/test_check_kmp_boundary.py` | every checker change | 13/13 |
| ktlint · lint · assemble · release-compile · unit tests · CodeQL | every PR | green |
| `:app:assembleDebug` | any dependency move (T1.2) | dex merge succeeds |
| `:app` unit tests | every PR | 104 suites / 1,215 / 0 |
| `:shared` tests | every PR | 13 / 216 / 0 |
| Browser walkthrough | T1.5, then each web-touching phase | every screen mounts, no console error |

Local build command (parent only): `/var/lib/hermes/bin/pylon-build.sh <tasks> --init-script /var/lib/hermes/.hermes/cache/scratch/mirror-init.gradle --console=plain`; preview: `:ui:wasmJsBrowserDevelopmentWebpack` then the tailnet URL.

## 12. Risks

- **Browser runtime** (back-stack save/restore, `ViewModelStoreOwner`) — documented, confirmed only at T1.5. If it fails, T1.2 grows a `rememberSaveable` seam; stays inside Phase 1.
- **Duplicate classes** at T1.2 if Google nav3 coords are added instead of replaced.
- **Phase 2 is mechanical but large** — ≤3 files per task, CI between batches, stop-if a seam is needed.
- **Memory** — no local full builds; wrapper serialises (75) and refuses under 3 GB free (76).
- **Child drift** — §4 is the mitigation; the parent re-verifies every deliverable.

## 13. Decisions log

1. **Web tokens — layered.** Session-only default (`InMemoryAuthStore`), opt-in WebCrypto vault. Protects against on-disk and cross-profile reading, **not** same-origin script; stated in SECURITY.md.
2. **Desktop — first-class code, deferred delivery.** jvmMain has 0 stubs and stays in CI; installer/tray/auto-update wait, because wasm is the most restrictive target: seams that satisfy wasm satisfy JVM.
3. **Room — Android only**, reasoned in §2.
4. **One graph + capability flags**, not per-target screen lists.
5. **JB nav3 artifact at 1.1.0**, Google coords removed in the same commit.
6. **T1.3 before T1.1 before T1.2** — chat's `Context` wrapper is the only thing that blocks a common graph, so it moves first.
