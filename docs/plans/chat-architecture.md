# Chat architecture — execution plan

Rewritten 2026-11-22. The slice-by-slice plan was correct but too slow to finish in one sitting:
each PR cost a 13-minute CI gate and bought 30–200 lines. This version batches by *area*, so each gate
buys hundreds of lines.

## Where we are (measured 2026-11-22 02:15 MSK)

- `ChatViewModel.kt` — **5,120 lines**, 60 public commands, ~100 private helpers.
- **112** places mutate screen state directly.
- `ChatUiState` — 56 fields, already split into five groups (transcript, rail, composer, connection,
  overlays) behind delegated getters.
- Rules already extracted to pure functions in `:shared`, with tests:
  transcript load + older page (16), clarify overlay, session install (14), dispatch classification (9),
  send admission/delivery (11), model label (4).
- Safety nets: **169** behaviour tests, router harness (3 scenarios), send-pipeline net (9).

## What "done today" means

Target after three batched PRs:

| | now | after today |
| --- | --- | --- |
| `ChatViewModel.kt` | 5,120 | ~3,600 |
| routers in the file | 631 lines | 0 |
| state groups without a reducer | 3 | 0 |
| tests | 169 + 28 | 169 + ~70 |

**Not today:** the plan's original acceptance ("under 600 lines, no direct state mutation"). That needs
the send-pipeline remainder, attachments, rail, search and streaming moved too — roughly eight more
hours of work and four more gates. Saying otherwise would be a lie with a date on it.

## The clock

- One CI gate ≈ **13 minutes**; unit tests dominate.
- The repo requires branches to be up to date, so **every merge invalidates its siblings**: rebase plus a
  fresh gate.
- Therefore **three PRs is the maximum for a two-hour window**, and batching is the only lever that buys
  real ground. Thin PRs are correct when review time is the scarce resource; tonight it is not.

## Batch 1 — routers leave the file

Move `handleWsEvent` (338 lines) and `handleRpcResult` (291 lines) into `ChatRouter.kt` as internal
extension functions on `ChatViewModel`. Widen the members they touch from `private` to `internal`.

Why this first: 631 lines are read by every future change, and the harness already gates the event side.

Gate: 169 + harness 3 + send net 9 + ktlint + compile.

## Batch 2 — the three missing reducers

Composer (12 fields), connection (3), overlays (18). Each transition becomes a pure
`reduce(state, intent)` in `:shared` with tests; the ViewModel calls it instead of editing fields inline.

Gate: the same suites plus the new reducer tests.

## Batch 3 — pipeline call sites onto intents

Attachment delegate and streaming controller stop writing state directly and emit intents; delete the
transition code that Batch 2 replaced.

Gate: the same suites.

## Rules that still hold

- No test assertion changes unless the assertion *is* the bug, and each one is named in the PR body.
- Each batch is reversible on its own.
- The plan doc ships inside the code PR, never as its own gate.

## Accepted risk

Widening `private` to `internal` weakens encapsulation inside the module. Accepted for today, with the
follow-up named: Batch 4 replaces the widened members with an explicit host interface, and only then
does the router become a real collaborator instead of a file split.
