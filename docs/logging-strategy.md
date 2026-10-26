# Logging strategy

Status: proposal. Applies to `:shared`, `:ui` (android/jvm/wasmJs), `:app`, and the
host side (gateway, dev proxy).

## The problem this is for

A real failure: `GET /api/status` timed out at 30 s from a phone while the same URL
answered `401` in 20 ms from the shell. Nothing recorded which request the app made,
nothing on the host recorded that a request arrived and never completed, and the two
sides had no shared identifier to join on. Every "why can't it reach the gateway"
report has that shape.

## Principles

1. **One record shape, everywhere.** Severity, time, component/tag, message, a
   small attribute map, optional error, and a request id. Both the client and the
   host emit this shape, with the same attribute names. It borrows the
   OpenTelemetry logs data model's *fields* without adopting the SDK (no collector
   here, and the SDK is a large dependency for an app that ships to a phone).
2. **Structure over sentences.** `NetworkLog` already shows why: `GET /api/status →
   HTTP 401 (18 ms)` is greppable, `something went wrong connecting` is not. Fields
   are added when a support question needs them, not speculatively.
3. **Correlation by construction.** The client mints a request id per outgoing
   request (`X-Pylon-Request-Id`), the host logs it and echoes it back. A user
   reporting "it failed at 14:02" gives an id that selects both sides.
4. **Redaction at the sink, not at the call site.** Call sites will forget. The
   sinks drop `Authorization`, `Cookie`, `Set-Cookie`, strip query strings, and
   replace secret values with `<redacted len=N>`. Bodies are never logged except
   under an explicit debug flag that a user turns on.
5. **Bounded by default.** Ring buffers in memory (no unbounded lists), rotated
   files on the host, and a hard capacity per sink. A logger must not be able to
   take the app down.
6. **Failures are always kept; successes are sampled.** Every transport failure and
   every 5xx/401 is recorded. High-volume successes are sampled at a low rate in
   release, and capped by the ring regardless.
7. **Export is user-initiated.** No continuous telemetry to a server we do not
   operate. "Share diagnostics" produces a text bundle (environment, build sha,
   checks, last N records) the user can paste. This is a privacy decision as much
   as a cost one.

## Where it lives

```
:shared/commonMain/util/HermesLog.kt        existing facade — the single entry point
:shared/commonMain/util/LogRecord.kt        the record shape (new)
:shared/commonMain/util/LogSink.kt          sink interface — extended with records (new shape)
:shared/commonMain/data/diag/NetworkLog.kt  becomes a RingSink consumer, not its own thing
```

Sinks, installed per target at app start:

| Sink | Android | Desktop | Web | Host |
|---|---|---|---|---|
| Platform console (`Logcat`, stdout) | yes | yes | console | stdout |
| Ring buffer (in-memory, 200 records) | yes | yes | yes | — |
| Rotated file | — | yes | — | yes (jsonl, logrotate) |
| Share bundle (on demand) | yes | yes | yes | — |

**Not adopting Kermit or Napier.** They supply severity levels and platform writers,
which `HermesLog` already does, and neither supplies correlation ids, redaction or a
share bundle — the three things that were actually missing. Adding one would mean
maintaining an adapter and a second configuration surface for no gain. If the
multiplatform logging ecosystem grows a structured-record standard worth adopting,
revisit; today the delta is ours to write either way.

## Levels

- `ERROR` — the operation failed and the user will see it. Always kept.
- `WARN` — degraded or retried, recoverable. Always kept, sampled above 100/min.
- `INFO` — state changes worth a support report (connect, sign-in, screen enter).
  Kept in the ring; not written to file on release builds.
- `DEBUG` — request/response summaries, WS frames. `isDebug` builds only, capped.

Release builds: platform sink at `WARN`, ring at `INFO`, file off. Debug builds:
everything, ring at `DEBUG`.

## Dev vs prod

Same pipeline, different policy. The record shape, sinks, redaction, correlation ids
and ring cap are identical in both builds; only verbosity and destinations differ.
That is deliberate: a logging system whose debug behaviour is a different code path
is a logging system that hides bugs in the path you ship.

| | Debug | Release |
|---|---|---|
| Console sink | `DEBUG` | `WARN` |
| Ring sink | on, `DEBUG` | on, `INFO` |
| Desktop file sink | on | off |
| Redaction | **identical** | **identical** |
| Request id minting | on | on |
| Failures (`ERROR`/`WARN`) | always kept | always kept |
| Share bundle | available | available |
| Bodies / WS frames | opt-in flag | off |

The switch is the seam that already exists: `BuildFlags.isDebug`
(`ui/src/commonMain/.../BuildFlags.kt`, an `expect object` whose Android actual reads
`BuildConfig.DEBUG`). Do not invent a second flag — one build-time truth, read in one
place.

Three things that must never be relaxed for development: redaction (we develop
against a real gateway with real cookies), request-id minting (one short string per
request), and failure retention. The ring stays on in release on purpose: field
diagnosis cannot depend on shipping a debug build, so "Share diagnostics" has to work
from the release artifact a user actually has.

**wasm has no `BuildConfig`.** There, "debug" vs "release" is decided by which bundle
was built — `developmentExecutable` (what the phone preview currently serves) versus
the `wasmJsBrowserDistribution` production bundle. The policy for the production
bundle is therefore `WARN` on the console and `INFO` in the ring, regardless of any
flag, because there is no flag to read.

## Enabled while developing

Phases 1 and 2 are always-on from the moment they land and need no toggle: a capped
ring costs microseconds per record and tens of KB, and a request-id header costs one
string per request. What stays behind the debug flag is body logging and the `DEBUG`
console level — and per-frame WebSocket logging stays off even in debug builds at
`INFO`, because it drowns the ring and costs bytes on wasm.

Usefulness is not theoretical. The failure that motivated this document would have
been one look: open Diagnostics, read the row, grep the host log for the same id —
instead of a screenshot round trip. During the remaining tranches (okhttp removal,
desktop/web persistence) it means seeing from the phone which call failed and how
long it took, with no `adb` in the loop. And the ring is testable in CI already:
`NetworkLogTest` is the pattern.


## Phases

1. **Shape + ring** (small): introduce `LogRecord`, keep `HermesLog`'s existing
   methods as wrappers so no call site changes, fold `NetworkLog` into a `RingSink`,
   give the ring one `StateFlow` that the diagnostics screen reads.
2. **Correlation**: `X-Pylon-Request-Id` in the Ktor client plugin, logged by the
   gateway and the dev proxy, echoed in responses, shown per row in Diagnostics.
3. **Share bundle**: "Copy diagnostics" producing the text bundle, on all targets.
4. **Host side**: JSON-lines access log on the dev proxy and gateway with the same
   field names, rotated, plus the request id — so the two sides join with one grep.

Each phase is its own PR and its own gate.

## What this is not

- Not a metrics or tracing system. If we ever need latency percentiles, that is
  OpenTelemetry with a real collector, not an extension of this.
- Not crash reporting. Uncaught exceptions still need a separate decision.
- Not chatty by default: a logger that floods is a logger nobody reads, and on wasm
  it also costs bytes every frame.
