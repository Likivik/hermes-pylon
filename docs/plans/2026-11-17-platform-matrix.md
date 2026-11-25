# Feature × platform matrix

Generated from the tree on 2026-11-17 by reading source sets, not from memory. Regenerate with the
same scan after a source-set change; a hand-edited cell is a claim nobody re-checks.

Source-set inheritance comes from `ui/build.gradle.kts`:

- `jvmAndroidMain` ← `androidMain`, `jvmMain`
- `desktopWebMain` ← `jvmMain`, `wasmJsMain`

So an `actual` in `desktopWebMain` serves **both** web and desktop, and one in `jvmAndroidMain` serves
**both** Android and desktop. A matrix that ignores this reports false gaps — the first draft of this
document did exactly that.

## Capability seams

| seam | Android | Web | Desktop | implemented in |
| --- | --- | --- | --- | --- |
| `BuildFlags` | yes | yes | yes | androidMain, jvmMain, wasmJsMain |
| `HermesChatImage` | yes | yes | yes | androidMain, desktopWebMain |
| `HermesImageBytes` | yes | yes | yes | androidMain, desktopWebMain |
| `HermesRemoteImage` | yes | yes | yes | androidMain, desktopWebMain |
| `MediaSurface` | yes | yes | yes | androidMain, desktopWebMain |
| `RequestNotificationPermissionOnStart` | yes | yes | yes | androidMain, desktopWebMain |
| `ToastEffect` | yes | yes | yes | androidMain, desktopWebMain |
| `atomicBoolean` | yes | yes | yes | jvmAndroidMain, wasmJsMain |
| `atomicInteger` | yes | yes | yes | jvmAndroidMain, wasmJsMain |
| `authStore` | yes | yes | yes | androidMain, desktopWebMain |
| `defaultServerBaseUrl` | yes | yes | yes | jvmAndroidMain, wasmJsMain |
| `diagnosticsEnvironment` | yes | yes | yes | androidMain, jvmMain, wasmJsMain |
| `dynamicColorSchemeOrNull` | yes | yes | yes | androidMain, desktopWebMain |
| `formatClockTime` | yes | yes | yes | androidMain, jvmMain, wasmJsMain |
| `formatIntegerGrouped` | yes | yes | yes | jvmAndroidMain, wasmJsMain |
| `formatMonthDayHourMinute` | yes | yes | yes | jvmAndroidMain, wasmJsMain |
| `holdsMonitor` | yes | yes | yes | jvmAndroidMain, wasmJsMain |
| `ioDispatcher` | yes | yes | yes | jvmAndroidMain, wasmJsMain |
| `isRetryable` | yes | yes | yes | jvmAndroidMain, wasmJsMain |
| `isRunningUnderTest` | yes | yes | yes | jvmAndroidMain, wasmJsMain |
| `isSafeHttpsAuthorizationUrl` | yes | yes | yes | jvmAndroidMain, wasmJsMain |
| `localUtcOffsetMinutes` | yes | yes | yes | jvmAndroidMain, wasmJsMain |
| `mediaStore` | yes | yes | yes | androidMain, desktopWebMain |
| `monotonicMillis` | yes | yes | yes | jvmAndroidMain, wasmJsMain |
| `nowMillis` | yes | yes | yes | jvmAndroidMain, wasmJsMain |
| `rememberAnalyticsCacheStore` | yes | yes | yes | androidMain, desktopWebMain |
| `rememberAppLanguageRestarter` | yes | yes | yes | androidMain, desktopWebMain |
| `rememberChatEnvironment` | yes | yes | yes | androidMain, desktopWebMain |
| `rememberChatInputLaunchers` | yes | yes | yes | androidMain, desktopWebMain |
| `rememberDownloadedFileLauncher` | yes | yes | yes | androidMain, jvmMain, wasmJsMain |
| `rememberExternalUrlLauncher` | yes | yes | yes | androidMain, jvmMain, wasmJsMain |
| `rememberFilesPlatform` | yes | yes | yes | androidMain, jvmMain, wasmJsMain |
| `rememberIs24HourFormat` | yes | yes | yes | androidMain, jvmMain, wasmJsMain |
| `rememberLogSharer` | yes | yes | yes | androidMain, jvmMain, wasmJsMain |
| `rememberMediaActions` | yes | yes | yes | androidMain, jvmMain, wasmJsMain |
| `rememberMediaPlayback` | yes | yes | yes | androidMain, desktopWebMain |
| `rememberPreferencesStore` | yes | yes | yes | androidMain, jvmMain, wasmJsMain |
| `rememberSensitiveCopy` | yes | yes | yes | androidMain, desktopWebMain |
| `secureRandomBytes` | yes | yes | yes | jvmAndroidMain, wasmJsMain |
| `serverBaseUrlIsPageOrigin` | yes | yes | yes | jvmAndroidMain, wasmJsMain |

**40 seams, 0 without an implementation on either shipping target.** A seam may still
return `null`, `false` or a no-op on one platform: that is a contract, documented at the declaration
(§3.1) and enforced by `scripts/check_actuals.py`, which rejects a silent constant return that has no
reasoned entry.

## Screens and their automated evidence

| screen | tests that name it |
| --- | --- |
| Achievements | **none** |
| Analytics | **none** |
| AuthLogin | LandingSharedTest.kt |
| Billing | **none** |
| Bots | **none** |
| ChannelDetail | **none** |
| Channels | **none** |
| Chat | **none** |
| Config | **none** |
| Connect | ProfileSeedingTest.kt |
| CronJobs | **none** |
| Diagnostics | **none** |
| Files | FilesScreenTest.kt |
| Gateway | **none** |
| Kanban | **none** |
| Keys | ScreenGraphTest.kt |
| Landing | LandingSharedTest.kt |
| Logs | **none** |
| McpServers | **none** |
| Model | FilesScreenTest.kt, ResProofTest.kt |
| Plugins | **none** |
| Processes | **none** |
| Profiles | **none** |
| Providers | **none** |
| Sessions | **none** |
| Settings | **none** |
| Skills | **none** |
| System | **none** |
| Toolsets | **none** |
| Webhooks | **none** |

24 of 30 screens have no test naming them. That is the honest limit of this
table: those screens are exercised only by the manual checks and by the browser boot test, which proves
the app composes but not each screen's behaviour.

## Suite coverage

| target | task | where |
| --- | --- | --- |
| web (wasmJs) | `:shared:wasmJsBrowserTest`, `:ui:wasmJsBrowserTest` | `unit.yml`, Chrome 157 on the runner |
| JVM | `:shared:jvmTest`, `:ui:jvmTest` | `unit.yml` |
| Android APK | `:app:testDebugUnitTest` | `unit.yml` |
| Android instrumented | — | not run: the multiplatform Android plugin exposes no host unit-test task for `:ui` (recorded blocker, not a silent drop) |
| browser end-to-end | `deploy/test/browser` | `browser-files-optional.yml`, `workflow_dispatch` only by standing decision |

The browser job asserts the app boots, the socket reaches `CONNECTED` with `lastFailure: "ready"`, and
that upload → list → download round-trips the same sha256.

## What this document does not claim

- Desktop is not a shipping target; it appears only because the source sets share code with web.
- "Has a test" means a test source names the screen; it does not mean every branch of that screen is
  covered.
- Nothing here measures visual correctness. That stays with the goldens and your eye.
