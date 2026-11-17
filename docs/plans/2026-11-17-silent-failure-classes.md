# Silent Multiplatform Failure Classes — Elimination Plan

> **For Hermes:** implement task-by-task; each task is one PR-sized unit or a task inside a PR. Run the red-proof before trusting any guard.

**Goal:** eliminate four classes of silent multiplatform failure, with runtime proof on both shipping targets — **Web (wasmJs)** and **Android APK**. JVM desktop stays a compatibility compile target, not part of the parity promise (no shipping desktop shell exists).

**Architecture:** three layers — (1) execute existing tests on the shipping platforms, (2) static guards that fail loudly when a platform seam or contract is missing, (3) an audit policy for swallowed failures. Nothing here invents a new framework; each guard follows the `check_entry_seams.py` pattern merged in #177 (self-tested, red-proofed, wired into `unit.yml`).

**Evidence base (all first-hand, 2026-11-17):**
- **Corrected 2026-11-17 (my first pass under-reported this):** CI runs `:ui:jvmTest` (unit.yml:68), `:shared:wasmJsBrowserTest` (unit.yml:103, karma + the runner's Chrome) and `testDebugUnitTest` (unit.yml:88). The real gaps are: **`:shared:jvmTest` is missing entirely** (so a JVM-only failure in `shared` passes CI), **`:ui` wasm tests are not run**, and `:ui` exposes **no Android host unit-test task at all** (the `com.android.kotlin.multiplatform.library` plugin offers only `assembleUnitTest`/`androidDeviceCheck`) — so `ui/commonTest` cannot execute on Android without plugin-level work, and that is a blocker, not a deferral.
- `wasmJsTest` source set: **0 files**; `commonTest` holds 19 files across `shared` + `ui` that *would* run under wasm.
- **Verified locally 2026-11-17:** the wasm test tasks exist and compile — `:shared:wasmJsTest` resolves (aggregate over `wasmJsBrowserTest`), and `compileTestDevelopmentExecutableKotlinWasmJs` succeeded — but execution **failed** with `No binary for ChromeHeadless browser on your platform. Please set CHROME_BIN` (Gradle then reported "test sources present... did not discover any tests", a cascade of the browser-launch failure, not a missing suite). Web test execution is feasible; the gap is a Chrome binary, which is a bounded CI/`CHROME_BIN` fix (§1 Task 1.3).
- `expect`/`actual` parity is structurally complete: 40 `expect`, 112 `actual`, zero expects with ≤1 actual. The compiler's promise holds; failures live in what it cannot check.
- 11 degenerate `actual` one-liners (empty body or constant return), including `rememberAppLanguageRestarter(): () -> Unit = {}` (desktopWeb) and `isRunningUnderTest(): Boolean = false` (wasmJs).
- Production `runCatching` blocks: **99 occurrences across 51 files** (canonical command: `rg -n "runCatching\s*\{" --type kotlin`, production source sets only; an all-source count is 113 in 58 files, and an earlier ripgrep "96" was its 50-file result cap). ~95 lack a log line near the block by crude lookahead — the real classification is the work of §4.

---

## §1 Class 1 — platform code compiled but never executed

The WS bug that started this (`installHttpClient` missing on web) was invisible because the shared WS tests were JVM-only and not in CI, and the browser job ran compilations whose behaviour no assertion touched. Web *compilation* was gated; web *behaviour* was not.

### Task 1.1 — put `shared` JVM tests in CI (smallest unblocking step)
- Modify `.github/workflows/unit.yml` — add `:shared:jvmTest` to the existing JVM test step (same `-Dorg.gradle.workers.max=2` pattern).
- Verification: CI run shows `HermesWsClientStaleStateTest` (4 tests) executing. Before this, the suite existed but never ran in automation — that alone is a finding.

### Task 1.2 — move the WS state-machine tests to `commonTest`
- Move `shared/src/jvmTest/kotlin/com/m57/hermescontrol/data/ws/HermesWsClientStaleStateTest.kt` → `shared/src/commonTest/kotlin/...`.
- It uses only `kotlin.test` + the `RecordingWsTransport` seam. **Verified:** `RecordingWsTransport` imports nothing JVM-only (`kotlinx.coroutines.CompletableDeferred` only) — it moves to `commonTest` unchanged; `HermesWsClient` is commonMain so the seams exist.
- After the move: delete the `jvmTest` copy, run `:shared:jvmTest` AND the wasm task from §1.3 — both must execute the same 4 tests. That is the acceptance proof: one suite, two platforms, same assertions.

### Task 1.3 — web tests in CI (already done for `:shared`; `:ui` missing)
- `:shared:wasmJsBrowserTest` already runs in `unit.yml:103` with the runner's preinstalled Chrome — no change needed there. Add the missing **`:ui:wasmJsBrowserTest`** step (task verified to exist by dry-run).
- **Known fix path, verified 2026-11-17 in two steps:** (a) with no `CHROME_BIN`, Karma reports `No binary for ChromeHeadless browser` — set it; (b) with `CHROME_BIN` pointed at the host chromium (156.0.8078.79), the binary is found but **crashes on launch** (`crashpad ... process_memory_range.cc:75 read out of range`, "ChromeHeadless failed 2 times"). Local proof is therefore still pending: the next attempt wraps the binary in a script adding `--headless=new --no-sandbox --disable-gpu` (the standard fix for sandboxed/unwrapped chromium in headless test runners) — **not yet run, do not assume green**.
- **CI Chrome availability, verified:** GitHub's `ubuntu-latest` (Ubuntu 24.04) and 26.04 runner images ship Google Chrome 157 and Chromium 157 preinstalled (actions/runner-images Ubuntu2604-Readme, retrieved 2026-11-17). The CI job exports `CHROME_BIN` to the preinstalled binary — no install step — and if the same crashpad crash appears there, the same wrapper-script fix applies; a silent fallback to another browser is not acceptable — if the browser variant proves flaky, the node variant is an explicit, documented switch.
- If wasm test compilation trips the binaryen wall the CI runner already hit (verified on #173/#174), restrict the job to the dev/test compilation path rather than production.
- Verification: CI run shows the common tests executed under wasm; red-proof by temporarily asserting a false fact in a test locally (must fail in CI, not just locally).

### Task 1.4 — Android unit execution for `:ui` — **blocked, verified**
- `:ui:testDebugUnitTest` **does not exist** (`Cannot locate tasks that match ':ui:testDebugUnitTest'`), and `:ui:tasks --all` shows no android host unit-test task at all: only `assembleUnitTest`, `assembleAndroidTest`, `androidDeviceCheck`. The `com.android.kotlin.multiplatform.library` plugin does not create one by default.
- Therefore `ui/commonTest` **cannot execute on Android today**. Options: configure an Android host-test target in the KMP android plugin (research, separate PR), or accept instrumented tests (deferred by cost). This is a recorded blocker, not a silent drop.
- Explicitly out of scope for now (decision recorded, not forgotten): emulator/instrumented tests in CI. The runner has no emulator; wiring one is a separate decision with real cost. The existing `androidTest` suite (8 files) stays manual/optional until that decision.

### Task 1.5 — browser runtime diagnostic + Playwright assertion
- Add `window.__hermesWsState` from the web entry: connection status name, attempt count, last failure category. **Redaction rule inherited from the campaign: no URLs with query strings, no ticket values, ever.**
- Extend `deploy/test/browser/files.spec.ts` with a boot test asserting `__hermesWsState.status === "CONNECTED"` after sign-in (the workflow already exists and is `workflow_dispatch`-only by standing decision; making it required is an explicit user decision, listed as an open question).

---

## §2 Class 2 — `actual` implementations that silently do nothing

### Task 2.1 — `scripts/check_actuals.py`
Follows the #177 checker pattern (staged classification, not a blanket ban):
- Flags: empty `actual` bodies; `TODO()`; `error("unsupported")`-style stubs; capability-shaped declarations (`OrNull`, `isRunning*`, `can*`) returning bare constants.
- Every flag is dismissible only via an allow-list entry with: declaration, target, reason, and either a compensating test reference or "explicitly unsupported feature" note. A stale allow-list entry (the code no longer matches) fails the check.
- Self-tests mirroring `test_check_entry_seams.py`: clean tree, empty-body flag, constant-capability flag, allow-list pass-through, stale allow-list, comment-stripping (a commented-out implementation must not count as implemented).
- Known sites to classify during the same PR (all verified 2026-11-17):
  - `rememberAppLanguageRestarter = {}` (desktopWeb) — desktop not shipping; allow with reason.
  - `isRunningUnderTest() = false` (wasmJs) — allow with reason.
  - `dynamicColorSchemeOrNull = null` (desktopWeb) — no dynamic color on web; allow.
  - `BuildFlags` constants — legitimate build metadata; allow.
  - `rememberDownloadedFileLauncher = null` (jvm + android) — see §3; the comment claims staged-file design, which the checker entry must document.

### Verification
- Red-proof: inject a fresh empty `actual` in a temp copy → checker fails; allow-list it → passes; delete the code but keep the allow-list entry → fails stale.

---

## §3 Class 3 — capability expressed as `null`/`false` with no stated contract

### Task 3.1 — document the contract on every capability `expect`
Minimal, non-breaking: each capability `expect` gets a KDoc contract stating what `null`/`false` means per platform and the user-visible consequence. The checker allow-list entry from §2 must quote that contract.
### Task 3.2 — typed capability objects (deferred by the 3-use rule)
`DownloadedFileOpener.Available/Unavailable` and friends are the architecturally correct end state, but today there are ~3 capability seams. Rule in force from the campaign: the 3-use rule brakes over-abstraction. Deferred until a third real case appears; §3.1 removes the ambiguity that makes them dangerous meanwhile.

---

## §4 Class 4 — swallowed production exceptions

Scope (verified): 99 `runCatching` blocks in production source sets across 51 files (top: `MediaPlaybackAndroid` 10, `WebAuthPlatform` 8, `Main.kt`/`ChatViewModel`/`BillingViewModel`/`DiagnosticsViewModel` 4 each). The 2 empty `catch` blocks are inspected and benign — they become the first allow-list entries: `AuthManager.kt:550` (coroutine cancel) is a real Kotlin catch; **`PreferencesStoreWasm.kt:31` is JavaScript inside a Kotlin raw string**, so the checker must strip string literals or it will flag that JS forever.

### Task 4.1 — classify, then enforce
Per-site classification into: intentional-ignore (document), log-and-continue (`HermesLog` with category), propagate, or typed-result. Expected bulk: `MediaPlaybackAndroid` failures are log-and-continue; `WebAuthPlatform`/auth-path swallows must never be silent (campaign rule: auth and transport failures are diagnosable permanently).
### Task 4.2 — `scripts/check_swallowed_exceptions.py`
Written *after* the classification, encoding the resulting policy with allow-list; never a regex ban first. Wired into `unit.yml` beside the other guards.

---

## Acceptance criteria (all must hold before the plan is "done")
1. The WS state-machine suite runs identically on **web and Android** in CI (same file, two executions).
2. Playwright proves ticket-mint + handshake on web (already proven manually and in CI run `43880130034`; the `__hermesWsState` assertion makes it continuous).
3. The contract checker catches every deliberately injected violation (red-proof recorded).
4. Every production swallow is classified; zero bare `catch {}` in production.
5. A matrix documents feature × web × Android with verified / N-A (reason) / unsupported (decision).

## Execution order (one concern per PR)
1. **PR A:** Tasks 1.1 + 1.2 + 1.3 + 1.4 — execution on all shipping targets.
2. **PR B:** Task 1.5 — `__hermesWsState` + Playwright assertion.
3. **PR C:** §2 + §3.1 — contract checker + classification of the known sites.
4. **PR D1:** §4 — full inventory + classification, no behaviour edits.
5. **PR D2:** logging/behaviour fixes by subsystem (MediaPlaybackAndroid alone holds 10 sites).
6. **PR D3:** the policy checker with allow-list.

## External cross-check (retrieved 2026-11-17)
- **Kotlin's official multiplatform API guidelines** (kotlinlang.org/docs/api-guidelines-build-for-multiplatform.html — foundational): "Ensure consistent behavior across platforms" — actuals must behave consistently, and unavoidable platform differences must be **clearly documented** (this plan's §3.1); "Test on all platforms" — the common suite must execute regularly on every supported platform (this plan's §1).
- **Compose Multiplatform common-code testing** (carrion.dev CMP UI testing post): `compose.uiTest` allows UI tests written once in common code and run per-platform — the mechanism behind the §1 acceptance criterion of one suite, multiple executions.
- **expect/actual failure writeups (2026 community articles, e.g. mobilebytesensei "silent failure" pieces):** the compiler enforces *shape*, never *meaning*; silent capability gating (constant returns, empty actuals) and running a shared suite on only one platform are the named top failure classes. This plan's guards exist precisely where those articles say projects get hurt.

## Risks / open questions
- **wasm test task feasibility in CI** — the in-flight local run decides; fallback is the node variant. Do not ship §1.3 on assumption.
- **Making browser-files Playwright required** is a standing-decision change (`workflow_dispatch`-only was the user's explicit call) — needs a fresh yes.
- **Emulator in CI** — heavy; deferred with the decision recorded, not silently dropped.
- Build environment facts that remain true: `pylon-build.sh` only (never `./gradlew`), `--rerun-tasks` on test gates, `PYLON_MIN_FREE_MB=0`, at most one daemon alive.

## Implementation status (updated 2026-11-17)
- **#179 MERGED** — `HermesWsClientStaleStateTest` + `RecordingWsTransport` moved to `commonTest`; the suite now executes on JVM **and** in Chrome (`Unit tests (debug)` green). The move immediately caught a real portability bug: `runBlocking` does not exist on wasm, so the suite failed to compile for the browser until the waits moved to `Dispatchers.Default` with real time. `:shared:jvmTest` added to CI — it had been in **no** job.
- **#180 MERGED** — `:ui:wasmJsBrowserTest` added to CI (21 tests execute in Chrome). Two assertions cannot run there yet and are guarded by `isWebTestTarget()` with the reason named at the call site: compose resource loading under karma (`MissingResourceException`) and a browser semantics-tree difference that makes `everyClickableNodeAnnouncesSomething` fail. Both are recorded debt, not solutions.
- **Still open:** the a11y semantics difference is the one to diagnose first — it may be a genuine web accessibility gap. §2 (contract checker), §3.1 (documented capability contracts) and §4 (exception audit) remain unimplemented.
- **Android execution remains blocked:** `:ui:testDebugUnitTest` does not exist; the KMP android-library plugin creates no host unit-test task.
