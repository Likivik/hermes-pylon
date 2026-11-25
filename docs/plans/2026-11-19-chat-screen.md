# Chat screen — plan (C1, C2, C3, C5, C6)

Date: 2026-11-19. Baseline `main@2750e043`. Evidence: `docs/chat-inventory.md`.
Isolated test bed: pod `pylon-inv` (gateway `127.0.0.1:19120`), preview `127.0.0.1:8091`.

C4 (paste) is **dropped**: the user confirmed paste works; the earlier finding only proved the
absence of *custom* clipboard code, not a broken path.

## Decisions (dated, closed)

- **DECIDED 2026-11-19 — C1 is not a rendering bug.** There is no empty-state composable:
  `grep` finds no empty branch in `ChatScreen.kt`, and an empty transcript legitimately draws
  nothing. The broken-image glyph seen in the headless screenshot is an unverified artifact
  (likely a missing emoji font in the headless stack), so it is *not* the defect. The defect is
  that the product has no empty state at all.
- **DECIDED 2026-11-19 — C2 is a keyboard gap, not a "no semantics" gap.** Measured on the
  running build: `#cmp_a11y_root` exists with **35 role nodes, 34 named**; **0 carry
  `tabindex`**, and the composer `textbox` has no accessible name. Compose Multiplatform 1.12
  enables web a11y by default but does not give the tree focus or state; upstream rewrote that
  part in **1.13.0-alpha01** (per-layer owners, inert background, focus). Decision: prefer the
  upstream bump, prove it with a spike, and write no custom DOM bridge unless the spike fails.
- **DECIDED 2026-11-19 — C3 is a deploy defect, not a build defect.** `ui.js` references more
  than one wasm chunk name; shipping only the newest made the browser fetch HTML for a missing
  chunk and abort with `expected magic word`. The fix is in the deploy step plus a boot assert.
- **DECIDED 2026-11-19 — C5 is an extraction, never a rewrite.** Six seams, 167 existing tests
  as the net; no test assertion changes.
- **DECIDED 2026-11-19 — C6 policy.** A failed convergence keeps the stale transcript, marks it
  stale, and offers retry. Every response carries a generation; a response whose generation is
  older than the current one is dropped, never applied.

## P0 — Fixtures and a boot assert (prerequisite for every phase)

Proven 2026-11-19 in our own pod: `tests/fakes/fake_llm_provider.py` (MIT, 463 lines, stdlib
only) copies into the gateway container and answers the OpenAI wire format at
`127.0.0.1:<port>/v1/chat/completions`. The image does not ship `tests/`, so the file is
vendored, not imported.

**Base — vendored from the gateway, unchanged:**

- `deploy/test/fake_llm_provider.py` with an attribution header. Gives scripted turns,
  SSE streaming, tool calls, reasoning blocks, provider faults, and a recording of every
  request. Replaces `stub_provider.py` on the chat path (`stub_provider.py` stays only for
  the Files/logs seeds, where a canned reply is the point).
- `deploy/test/fixtures/session-resume-active-turn.json` — the authoritative resume payload
  (`inflight`, `status: working`, `resumed`, `running`); use it instead of hand-built shapes
  in the resume tests.

**Our layer — the part upstream cannot give us:**

- `deploy/test/chat_scripts.py`: named scripts the UI probes request by name — long markdown
  with fenced code, a 2,000-line tool output, a failing tool, an image link, an approval
  request, a clarify prompt, an interrupted stream, a slow stream.
- `seed_sessions()`: two sessions with fixed ids built by *running* those scripts through the
  gateway, so message shapes are the gateway's, not ours.
- `boot_assert.py`: exits 0 only when the composer painted and `#cmp_a11y_root` has a named
  node; non-zero with the console error otherwise.

**Acceptance:** `seed.py sessions` twice leaves exactly two sessions (idempotent);
`boot_assert.py` exits 0 against a good bundle and non-zero against a bundle with one renamed
wasm chunk; each named script is addressable by the UI probe without editing Python.

## P1 — C3: deploy that cannot half-ship

- Copy every chunk `ui.js` references, discovered by parsing `ui.js`, not by `ls | head -1`.
- Wipe stale artifacts from the served root before the copy, so an old hash can never be
  requested.
- Run `boot_assert.py` after the swap; a failure exits non-zero and leaves the previous build
  served.

**Acceptance:** with the build directory containing a stale Nov-2 artifact, a deploy results in
a serving root whose chunks all answer `200 application/wasm`, and `boot_assert.py` exits 0.
Deleting one chunk makes the deploy fail loudly instead of serving a broken app.

## P2 — C1: an empty state that says what to do

- Empty transcript renders: one line of intent ("Start a conversation"), the model selector, and
  nothing else that looks like content.
- The state has semantics: a heading role and a text alternative, so a screen reader announces it.
- No image, no glyph: text only, so a missing font can never look like a broken asset.

**Acceptance:** `EmptyStateTest` (composable test) asserts the text and the heading semantics;
the dev build's first screen shows the line, and a build with an emoji-free font stack shows no
broken-image box.

## P3 — C2: a keyboard-reachable tree

- **Spike first (1 day, timeboxed).** Bump Compose Multiplatform to `1.13.0-alpha01` in a
  throwaway branch; run the existing gates; read `#cmp_a11y_root` and record `tabindex` count,
  focus order, and whether a closed sheet leaves the tree. Decision recorded in this file with
  the measured numbers.
- If the spike shows focus: adopt the bump. If it does not: write the smallest shim that adds
  `tabindex` to pressable roles, keeps Enter/Space activating the node, and grants Escape back to
  the canvas — marked as temporary, with the removal condition recorded.
- Name the composer: `Modifier.semantics { contentDescription = "Message" }` (label text, not
  placeholder), and give the send button a state description while streaming.

**Acceptance:** a CDP audit reports `tabindex` on every pressable node, Tab moves through the
composer → send → rail → header in reading order, Enter activates the focused button, and the
composer announces its name. The audit runs in CI as `scripts/a11y_audit.py` with a non-zero
exit on regression.

## P4 — C6: convergence that cannot lie or race

- Each load carries a generation; applying a response requires generation ≥ current.
- A failed refresh leaves content, sets `staleSince`, and shows a non-blocking chip with Retry.
- A profile switch or session switch bumps the generation and drops in-flight results.

**Acceptance:** `ConvergenceTest` covers (a) late response for an older generation is dropped,
(b) failed refresh keeps stale content and sets the chip, (c) Retry clears the chip on success.
No assertion in existing tests changes.

## P5 — C5: split `ChatViewModel` by seam

Six extractions, each its own PR, tests unchanged throughout:

1. `ConnectionCoordinator` — socket state, tickets, reconnect policy.
2. `TranscriptRepository` — cache, paging, hydration, generations (P4 lives here).
3. `SendQueue` — optimistic bubble, request id, retry, refusal mapping.
4. `StreamAssembler` — deltas, tool calls, cancellation.
5. `AttachmentStager` — pick, preview, upload, limits.
6. `ChatViewModel` — composition and intent dispatch only, target under 600 lines.

**Acceptance:** after each extraction the 167 tests pass with no edits, `ChatViewModel.kt` line
count drops monotonically, and no public behaviour changes (same UI, same logs).

## Risks

- **Compose alpha under a shipping app.** Mitigation: the spike is measured, not assumed; the
  bump lands only if the full suite and the pixel-level screens are unchanged.
- **A11y shim rot.** Mitigation: dated removal condition tied to the Compose version that ships
  focus upstream, and the audit script fails if the shim is present on that version.
- **ViewModel extraction touching 167 tests.** Mitigation: seam-by-seam PRs, no assertion edits,
  behaviour-free moves first.
- **Headless screenshots are not the product.** Mitigation: every visual claim is confirmed on
  the phone before it becomes a "fix"; C1 already shows how a font artifact can look like a bug.
