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

Today there are exactly **two** `expect` declarations, which is the real measure
of how thin the platform surface is:

| Expect | Android | JVM |
|---|---|---|
| `PlatformClock.nowMillis()` | `System.currentTimeMillis()` | `System.currentTimeMillis()` |
| `isRetryable(IOException)` | socket/TLS exception taxonomy | same |

## Adding a target

Because `HttpClient { }` in `commonMain` resolves its engine from the classpath,
adding desktop (already present as `jvm`) or web costs four small steps:

1. Declare the target in `shared/build.gradle.kts`.
2. Add its engine — `ktor-client-cio`/`okhttp` for JVM, `ktor-client-js` for web.
3. Provide `actual`s for the two `expect` declarations.
4. Add a `commonTest`-visible test run for the target.

No shared code changes. That is the property this boundary exists to protect.

## Enforcing it

```bash
python3 scripts/check_kmp_boundary.py        # exits non-zero on violation
python3 scripts/test_check_kmp_boundary.py  # tests for the checker
```

The checker also reports any `expect` without a matching `actual` in a target
source set, so a half-migrated target fails loudly instead of compiling into
something subtly wrong. Run it before merging anything that touches
`shared/src/commonMain`.
