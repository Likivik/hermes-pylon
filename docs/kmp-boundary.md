# The multiplatform boundary

`:shared` is the code that is not allowed to know what it is running on. `:app`
is the Android client built on top of it. The boundary between them is the thing
that makes a future desktop or web client cheap, so it is enforced rather than
assumed.

## The rule

**`shared/src/commonMain` must not import platform APIs.** No `android`,
`androidx`, `java`, `javax`, `okhttp3` or `retrofit2`. Anything platform-specific
lives in a target source set (`androidMain`, `jvmMain`, and any future
`jsMain`/`wasmJsMain`) and reaches shared code through an `expect`/`actual`
declaration.

Platform types are welcome in *comments* — KDoc records what a declaration
replaced, and that history is worth keeping. Only code is checked.

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

1. Declare the target in `shared/build.gradle.kts`.
2. Add its engine — `ktor-client-cio`/`okhttp` for JVM, `ktor-client-js` for web.
3. Provide `actual`s for every `expect` declaration (eight today).
4. Add a `commonTest`-visible test run for the target.

**This page used to claim "no shared code changes". That was wrong**, and
measurably so: adding a `wasmJs` target to a `commonMain` that passed this
checker — and every import grep — produced **38 compile errors**, every one of
them in `commonMain`.

The reason is that `:shared:compileCommonMainKotlinMetadata` is **SKIPPED** while
every declared target is JVM-based, so `commonMain` was only ever compiled *per
target, against the JVM stdlib*. `System.nanoTime()`, `@Volatile`, `javaClass`,
`Charsets`, `String.format`, `Dispatchers.IO` and `runBlocking` all resolved
there, and none of them are common.

Import-level checking cannot see that class of error, because it is not an
import. Keeping a non-JVM target declared is what forces the metadata compilation
to actually run — and that is what makes this page's promise true instead of
aspirational.

## Enforcing it

```bash
python3 scripts/check_kmp_boundary.py        # exits non-zero on violation
python3 scripts/test_check_kmp_boundary.py  # tests for the checker
```

The checker also reports any `expect` without a matching `actual` in a target
source set, so a half-migrated target fails loudly instead of compiling into
something subtly wrong. Run it before merging anything that touches
`shared/src/commonMain`.
