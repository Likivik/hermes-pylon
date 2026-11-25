# The multiplatform boundary

`:shared` is the code that is not allowed to know what it is running on. `:app`
is the Android client built on top of it. The boundary between them is the thing
that makes a desktop or web client cheap, so it is enforced rather than assumed.

`:ui` is the second shared module: every screen, view model and the data layer.
It is Compose Multiplatform, so a slightly different rule applies to it — see
"The `:ui` rule" below.

## The rule

**`shared/src/commonMain` must not import platform APIs.** No `android`,
`androidx`, `java`, `javax`, `okhttp3` or `retrofit2`. Anything platform-specific
lives in a target source set (`androidMain`, `jvmMain`, and any future
`jsMain`/`wasmJsMain`) and reaches shared code through an `expect`/`actual`
declaration.

Platform types are welcome in *comments* — KDoc records what a declaration
replaced, and that history is worth keeping. Only code is checked.

## The `:ui` rule

Compose Multiplatform republishes Compose, lifecycle and navigation for every
target, so those imports are legitimate in `ui/src/commonMain`. Allowed:

```
androidx.compose         androidx.lifecycle     androidx.navigation3
androidx.datastore       androidx.annotation    androidx.collection
androidx.graphics        androidx.savedstate
```

Forbidden — the same platform roots as `:shared`, plus **any `androidx.*` that is
not on that list**:

```
android          java             javax
okhttp3          retrofit2        androidx.<anything else>
```

That last clause is the useful one: it stops Room, WorkManager, Keystore or an
activity-scoped artifact from drifting into shared UI code, which is how a
"multiplatform" screen quietly becomes Android-only again.

Measured today: **0 forbidden imports** and every expect has its actuals in both
modules. `:ui/commonMain` uses `androidx.compose` (2,540 imports),
`androidx.lifecycle` (115), `androidx.navigation3` (4), `androidx.datastore` (2).

## What is already shared

The whole gateway surface, and the logic around it:

* `HermesGatewayApi` — all 142 endpoints on Ktor, plus `GatewayResponse`.
* `NetworkResult` / `NetworkError` / `safeApiCall` / `mapHttpError` — retry,
  backoff and HTTP-error classification as pure multiplatform logic.
* `ServerEndpoint` / `ServerBaseUrl` — URL parsing, validation, cleartext policy.
* `ChatMessage`, `ChatUiModels`, `ChatWsEventReducer`, `ChatMessageSync`,
  `TodoHierarchy`, `RailGrouping` and the rest of the shared domain model.
* `HermesJson` — the single wire codec, configured once.

Today there are **eight** `expect` declarations, and **no** `expect class` among
them — every one is an `expect fun`/`expect val`. Three are the thin,
platform-shaped kind this design wants; five exist only because `commonMain` may
not name `java.util.concurrent` types, and are *factories* returning plain common
interfaces rather than the types themselves.

| Expect | Android | JVM |
|---|---|---|
| `PlatformClock.nowMillis()` | `System.currentTimeMillis()` | `System.currentTimeMillis()` |
| `isRetryable(IOException)` | socket/TLS exception taxonomy | same |
| `ioDispatcher` | `Dispatchers.IO` | `Dispatchers.IO` |
| `concurrentMutableMap()` | `ConcurrentHashMap` | `ConcurrentHashMap` |
| `removeIfValue(key, value)` | `java.util.Map.remove(k, v)` | same |
| `atomicInteger(initial)` | `AtomicInteger` | `AtomicInteger` |
| `atomicBoolean(initial)` | `AtomicBoolean` | `AtomicBoolean` |
| `concurrentLinkedQueue()` | `ConcurrentLinkedQueue` | `ConcurrentLinkedQueue` |

The last five are factories on purpose. `expect`/`actual` **classes** are still
**Beta** on Kotlin 2.4.x — the compiler warns, and silencing it needs
`-Xexpect-actual-classes` — so the concurrent types are declared as plain common
interfaces and built by an `expect fun`, which has been Stable since 1.9.20. No
`expect class` is left in `commonMain`; prefer this shape for anything new.

## Adding a target

Because `HttpClient { }` in `commonMain` resolves its engine from the classpath,
adding a target costs four small steps:

1. Declare the target in `shared/build.gradle.kts` and `ui/build.gradle.kts`.
2. Add its engine — `ktor-client-cio`/`okhttp` for JVM, `ktor-client-js` for web.
3. Provide `actual`s for every `expect` in **both** modules.
4. Fill in the `:ui` seams: `AuthPlatform`, `SecretStore`, `CookiePlatform`,
   `FilesPlatform`, `ExternalUrlLauncher`, `AnalyticsCacheStore`,
   `rememberLogSharer`, `readAppLogFiles`.
5. Add a `commonTest`-visible test run for the target.

`:ui` declares 36 more `expect`s (auth, secure storage, cookies, files, logs,
analytics, random bytes, monitor ownership). Step 3 covers both modules, and
step 4 lists the seams a new target has to fill.

**This page used to claim "no shared code changes". That was wrong**, and
measurably so: adding a `wasmJs` target to a `commonMain` that passed this
checker — and every import grep — produced **38 compile errors**, every one of
them in `commonMain`.

The reason is that `:shared:compileCommonMainKotlinMetadata` is **SKIPPED** while
every declared target is JVM-based, so `commonMain` was only ever compiled *per
target, against the JVM stdlib*. `System.nanoTime()`, `@Volatile`, `javaClass`,
`Charsets`, `String.format`, `Dispatchers.IO` and `runBlocking` all resolved
there, and none of them are common.

The same trap has a second face, hit during the `AuthManager` move:
`synchronized` and `Thread.holdsLock` are JVM-only but are not imports. Both now
live behind seams (atomicfu locks, `expect fun holdsMonitor`); new common code
must be written without them.

Import-level checking cannot see that class of error, because it is not an
import. Keeping a non-JVM target declared is what forces the metadata compilation
to actually run — and that is what makes this page's promise true instead of
aspirational.

## Enforcing it

```bash
python3 scripts/check_kmp_boundary.py        # exits non-zero on violation
python3 scripts/test_check_kmp_boundary.py  # tests for the checker
```

The checker runs over **both** `:shared` and `:ui`, applies each module's policy,
and reports any `expect` without a matching `actual` in a target source set, so a
half-migrated target fails loudly instead of compiling into something subtly
wrong. It runs in CI (`.github/workflows/unit.yml`) and in
`scripts/local-tests.sh unit`. Run it before merging anything that touches a
`commonMain`.
