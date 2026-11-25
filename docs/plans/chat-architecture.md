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

## Batch 2 — gateway requests leave the file (revised after measuring)

The plan said "composer, connection, overlays reducers". Measured before building: connection is
**3 fields and 5 setters**, so a reducer there is ceremony — the same indirection-without-a-rule that
this plan already rejects for the five thin `handleRpcResult` branches.

Replaced with an area that is genuinely large and has a hard boundary: the flows where the *gateway*
asks the *user* for something.

Moved to `ChatServerRequests.kt`: `handleServerRequest` (152), `handleServerRequestCancelled` (74),
`handleApprovalRequest`, `handleSudoRequest`, `handleSecretRequest`, `clearPrivilegedControls`,
`expiryMatches`. **`ChatViewModel.kt`: 4,491 → 4,172.**

Why this area matters more than a reducer: a dropped approval or secret event does not produce a stale
pixel, it hangs an agent thread.

### Where the reducers went

Composer and overlays are still the honest target for real rules, and they are **Batch 3** now. They were
not ceremony — the plan was just wrong about connection.

## Batch 3 — pipeline call sites onto intents

Attachment delegate and streaming controller stop writing state directly and emit intents; delete the
transition code that Batch 2 replaced.

Gate: the same suites.

## What Batch 3 actually taught (2026-11-22)

Three attempts to move `requestRailRefresh` + `fetchRail` into `ChatRail.kt` as extensions, three
reverts. All three failed the same way, and the failures are the useful part:

1. **A function already marked `internal` keeps its `internal`, so the rewrite to
   `internal fun ChatViewModel.x(...)` never fires** — the function lands in the new file with no
   receiver, and every reference to `this` inside it breaks.
2. **Constructor properties are not widened by a 4-space regex.** `private val api: ChatApi` lives in the
   class header; moving code that calls `api` silently requires editing the header by hand.
3. **Functions that own mutable counters cannot be cut out at all.** The rail holds `railSeq`,
   `railInFlight`, `railNextAllowedAt`, `railFailures`; history holds refresh generations, owner tokens
   and job handles. Each extraction cut declarations that sit *between* the moved functions, which is how
   one attempt produced a duplicate `fetchRail` and 1,200 cascading errors.

Batches 1 and 2 worked because routers and gateway handlers mostly *read* state, and the few members they
wrote were simple flags. An area that *owns* state needs a class with explicit inputs and a callback for
its writes — the shape `ChatHistoryLoader` already has, which is why that one compiled first time.

**Rule for Batch 4 and onward:** extensions for readers, classes for owners. Do not reach for a script
when the area has counters.

## Rules that still hold

- No test assertion changes unless the assertion *is* the bug, and each one is named in the PR body.
- Each batch is reversible on its own.
- The plan doc ships inside the code PR, never as its own gate.

## Accepted risk

Widening `private` to `internal` weakens encapsulation inside the module. Accepted for today, with the
follow-up named: Batch 4 replaces the widened members with an explicit host interface, and only then
does the router become a real collaborator instead of a file split.
