# Chat architecture — decision and migration plan

Date: 2026-11-20. Base: `main@2a07d9eb`. Evidence gathered by measurement, not recall.

## What exists today (measured)

| Fact | Value |
| --- | --- |
| `ChatViewModel.kt` | 4,798 lines, 166 functions |
| Public commands on the ViewModel | 60 |
| Fields in `ChatUiState` | 56 |
| `_uiState.update` call sites | 112 (and **0** direct `_uiState.value =` writes) |
| `viewModelScope.launch` | 53 |
| `catch` blocks in the ViewModel | 10 |
| UI files reading `ApiClient.` directly | 28 |
| `ChatBubble.kt` / `MarkdownText.kt` | 2,693 / 1,951 lines |
| `ChatViewModelTest.kt` | 5,736 lines, 169 cases |

Style today: MVVM in form (one immutable state holder, one ViewModel), a fat view model acting as
controller, transport adapter and persistence coordinator. Delegates exist (`streamingController`,
`attachmentsDelegate`, `searchDelegate`, `ServerMessageMapper`, `TranscriptCache`,
`ApprovalExpiryScheduler`, `ClarifyBatching`) but form no layer.

What is already good and must survive the migration: one immutable state with no direct mutation,
typed protocol boundaries (`WsEvent`, `MutationRefusal`, classified 409s), named guards
(generation/fence/owner), and 169 behaviour-level tests.

## Decision (2026-11-20)

**Adopt MVI incrementally** — state as data, transitions in pure reducers, effects in handlers — in
four steps, each its own PR, with the 169 tests as the entry condition for every step.

Rejected:
- **Layering only (B)**: fixes dependency injection but leaves the 56-field state and its illegal
  combinations in place — the exact class of bug behind the overlay flash and the icon issue.
- **Full rewrite**: would rewrite 169 working behaviour tests in one commit. The tests are the only
  net; discarding them converts an architecture improvement into a regression gamble.
- **Status quo (finish C5 seams only)**: leaves the state model untouched; seams shrink the file but
  not the failure modes.

## What MVI buys, stated honestly

It buys **illegal states unrepresentable** and **transitions testable without coroutines**.

It does **not** buy ordering or concurrency correctness. A response arriving after a session switch
is an event-ordering problem, solved by tagging each event with the generation it belongs to and
dropping stale ones — the fence and owner-counter work already in place. No reducer replaces that.

## Step 0 — Dependencies injected (prerequisite)

`ChatViewModel` reaches three globals: `private val wsClient = HermesWsClient` (a Kotlin `object`),
`ApiClient` (read directly in 28 UI files), and preferences. Static mocking is why the test file is
5,736 lines.

- Introduce `ChatDependencies`: the transport, the API, the repository, preferences, the clock.
- Production wiring in one place; tests pass fakes.
- No behaviour change: the same calls, reached through the seam.

**Acceptance:** `grep -c "HermesWsClient\b" ChatViewModel.kt` is 0 outside a type parameter;
`ApiClient.` appears in no file under `ui/chat/`; 169 tests pass unchanged; the test file compiles
without static mocking of `ApiClient` or `HermesWsClient`.

## Step 1 — State split (no behaviour change) — DONE 2026-11-20 (#223)

`ChatUiState`'s 56 fields become **five** sub-states, composed into the same public `ChatUiState`.
Five, not four: the session rail was split out (decision 2026-11-20) because its fetch lifecycle and
failure mode are independent of the conversation, and folding it into transcript would rebuild the
coupling this step removes.

- `TranscriptState` — messages, paging offsets, generations, `staleSince`, cache ownership.
- `ComposerState` — draft, attachments, prefill, model selection, slash state.
- `ConnectionState` — status, `hasConnectedOnce`, profile binding, reconnect bookkeeping.
- `RailState` — session rows, refresh flag, load status, picker visibility.
- `OverlaysState` — approval, clarify, dialogs, search, model picker, transient errors.

Fields that only make sense together move together.

**Result, measured:** 206 tests pass with **no assertion changes**. 33 `ChatUiState(...)`
*constructions* had to name their group. `ChatUiState` keeps every public field name as a getter, so
all reads compile unchanged; writes now name the owner (`copy(transcript = transcript.copy(…))`), 186
of them.

**Deferred:** constructor-time invariants (e.g. `staleSince != null` requires non-empty messages).
Adding a `require()` before the transitions that produce those states are enumerated would turn
currently-tolerated states into crashes; the reducers in Step 2 are where each transition becomes a
closed list, and the invariants follow from them.

## Step 2 — Reducers, transcript first — DONE 2026-11-20 (#224, #225, #227)

Landed 2026-11-20: `TranscriptReducer` with `LoadStarted`, `HistoryLoaded`, `HistoryFailed` (#224);
`OlderLoadStarted`/`OlderLoaded` (#225); clarify-overlay transitions through their own reducer (#227).
16 reducer tests. `ChatRouterCharacterizationTest` (#229) pins the router's three core guarantees so the
Step 3 moves below are gated by a test written against `main`.

- `TranscriptReducer.reduce(state: TranscriptState, intent: TranscriptIntent): TranscriptState` — pure.
- The existing load/append/merge/stale transitions are re-expressed as intents and the ViewModel
  methods delegate to the reducer, keeping their signatures.
- Effects (network, persistence) stay where they are; they emit intents when they complete.

**Acceptance:** every transition that `loadSessionMessages`, `loadOlderMessages` and `retryHistory`
perform today is expressible as an intent; reducer tests cover late-response drops, stale marking,
and clearing; the 169 tests still pass unchanged; the ViewModel has no transcript-state mutation
outside the reducer.

## Step 3 — Retire the ViewModel, area by area — IN PROGRESS

Started 2026-11-20. Each PR moves a *rule* out of `handleRpcResult` into `:shared` as a pure function
with tests, leaving the effects (fences, holders, persistence, job cancellation) in the ViewModel.

Landed:

- **3a (#231)** — `withInstalledSession`, `withNoSessionModel`, `withSessionModel`: session create and
  branch. Surfaced an undocumented difference: a branch replaces `contextUsage` even with `null`, a
  create leaves it alone.
- **3b (#233)** — `withResumedSession`, `withResumedModel`: the resume ack. Found the third
  model-label implementation and fixed a real bug — resume built `"$provider/$model"` unconditionally,
  so a pre-qualified model became `openai/openai/gpt-5`.
- **3c (#235)** — `classifyDispatchResult` / `DispatchOutcome`: `command.dispatch` as data. Documented
  the asymmetry that `exec` falls back to `message` and `plugin` does not.

`handleRpcResult`: 322 → ~230 lines.

What remains in it, and why it is not a rule to extract: `SESSION_LIST` (4 lines), `SESSION_CLOSE` (7),
`SESSION_INTERRUPT` (11), `COMMANDS_CATALOG` (12), `APPROVAL_RESPOND` (20) are thin delegations to
existing functions — `requestRailRefresh`, `submitPrompt`, `streamingController.reset`, and so on.
Extracting the delegation would add indirection without a testable rule. Step 3 continues when those
*functions* move, not when their call sites are wrapped.

Next candidates, in order of payoff: the send pipeline (~250 lines, no tests today), then
`handleWsEvent` (342 lines) behind the harness.

**Acceptance:** `ChatViewModel.kt` under 600 lines; no `_uiState.update` in it; the 60 public
commands are either intent constructors or thin wrappers; each retired area has reducer tests equal
in coverage to the behaviour tests it replaced, and those behaviour tests keep passing.

## Invariants for every step

- No test assertion changes unless the assertion encodes the behaviour being replaced, and each such
  change is named in the PR body.
- Each step is reversible: no step deletes a code path before its replacement is covered.
- `ChatUiState`'s public shape does not change in Steps 0–2. Callers see no difference.
- Every step ends with: 169/169, `ktlintCheck`, `:ui:compileKotlinWasmJs`, `:ui:compileTestKotlinWasmJs`.

## Risks

- **Type explosion.** 60 commands as intents is ~120 types. Mitigation: intents only for transitions
  that change state; pure reads stay methods.
- **Reducer becomes the new god object.** Mitigation: one reducer per sub-state, and a reducer may
  not call another's.
- **Tests couple to field names.** Mitigation: Step 1 keeps delegated property names, so tests
  compile untouched — if they need edits, the split is wrong.
- **Migration stalls halfway.** Mitigation: each step stands alone and is shippable; no step depends
  on the next one landing.
- **Concurrency mistaken for state.** Mitigation: this plan explicitly assigns ordering to fences and
  generations, and reducers must not attempt to model it.

## Not in this plan

`ChatBubble.kt` (2,693) and `MarkdownText.kt` (1,951) — separate rendering work, tracked as a
follow-up. The event router's physical move waits for the characterization harness described in
`docs/plans/2026-11-19-chat-screen.md` (P5).
