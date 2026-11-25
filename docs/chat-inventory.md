# Chat screen — defect inventory (batch 1, measured)

Date: 2026-11-19. Build: `main@2750e043`, dev bundle `96bcf18a…wasm`.
Environment: isolated pod `pylon-inv` (gateway `127.0.0.1:19120`), preview `127.0.0.1:8091`,
browser CDP. Nothing here touches the live gateway on `:9119`.

Batch 1 is what the code and a running build prove without human pacing. Batch 2 needs
coordinate-driven interaction (see "Probe matrix").

## Findings

### C1 — The empty state pulled an 11 MB font for one glyph (payload, P2) — FIXED
Third and final correction: an `EmptyState` does exist (`"Ready to chat"`), and the torn glyph in
the screenshot was a Material Symbols glyph, not an image. The real defect: `EmptyState` defaulted
to `icon = "inbox"`, `inbox` was **not** in the 108-name subset font, so the fallback fetched
`material_symbols_outlined.ttf` — **10.7 MB** — for the first empty screen of a session.
Fixed by adding `Inbox` to `HermesIcon` (subset now 109 names, 209,748 bytes) and guarding every
icon literal with `scripts/check_icon_literals.py`. Measured after: full-font requests **0**,
fonts loaded = `onest_variable 61 KB + subset 91 KB`.
Closed 2026-11-20: the user confirmed on the phone that the empty screen renders its icon and text.
The missing title/subtitle in the headless a11y tree and the box glyph were headless artifacts, so
no rendering bug exists here — the defect was the 10.7 MB fetch, and that is fixed.

### C2 — The a11y tree exists but has no keyboard access (accessibility, P1)
Measured in the live page inside the shadow root: `#cmp_a11y_root` holds **35 role nodes, 34
named**; **0 have `tabindex`**; the composer `textbox` is unnamed. Compose Multiplatform 1.12
enables web a11y by default but supplies no focus, state or live regions; upstream rewrote that
in 1.13.0-alpha01. Acceptance: every pressable node is tabbable, Tab order follows reading
order, Enter/Space activate, and the composer has an accessible name — enforced by
`scripts/a11y_audit.py` in CI.

### C3 — Deploying the dev bundle can brick boot with a wasm "corruption" (tooling, P1)
`ui.js` references more than one wasm chunk name; the build directory still held a Nov 2
artifact. Shipping only the newest chunk made the browser fetch HTML for the missing chunk
and abort with `expected magic word 00 61 73 6d, found 3c 21 64 6f`. It looks like a corrupt
build to whoever hits it. Acceptance: a deploy step copies every chunk `ui.js` references
(or wipes the output dir first) and asserts a boot check after the swap.

### C4 — No clipboard path on wasm (input, P1, runtime unverified)
`grep` over `ui/src/**` finds clipboard code only in `LogBundleSharingWasm.kt`. Chat has no
paste handling and no `onPaste` hook, so paste depends entirely on Compose-web defaults.
Carried evidence says paste silently fails. Acceptance: pasting text into the composer
inserts it, pasting an image attaches it, and both have a test at the composable level.

### C5 — One file holds every chat concern (structure, P1)
`ChatViewModel.kt` 4,821 lines; `ChatBubble.kt` 2,693; `MarkdownText.kt` 1,951;
`ChatScreen.kt` 856. `ChatViewModel` alone has 10 `catch (` blocks and 4 `runCatching` —
silent-failure surface concentrated where the state machine lives. Acceptance: connection,
transcript, send, streaming, attachments and paging are separate objects; the 167 existing
tests pass unchanged.

## Regression guards to add (from this week's fixes)

- Overlay appears only before the first successful connect; a status blip never blanks a
  populated screen (PR #210).
- A switch paints from cache, converges, and a failed convergence leaves stale content with
  a non-blocking error (PR #210) — **defined behaviour still missing**, see C6.
- No protocol notice ever enters the transcript (PR #210).

### C6 — Cache convergence failure has no defined behaviour (risk, P1)
The new cache paints instantly, then the authoritative load runs. If that load fails, the
screen keeps stale content with no signal that it is stale, and nothing prevents an
out-of-order response from overwriting a newer one. Acceptance: a failed refresh shows a
non-blocking stale marker with retry; responses carry the request generation and a stale
response is dropped; a test covers both.

## Probe matrix (batch 2, needs coordinate interaction)

| Surface | What to record |
| --- | --- |
| send | latency, optimistic bubble, failure state, retry |
| streaming | partial text cadence, scroll follow, cancel |
| tool calls | collapsed/expanded, long output, error output |
| approval sheet | appears, blocks composer, timeout |
| clarify sheet | options render, answer path |
| attachments | pick, preview, remove, oversize, on web |
| slash menu | filtering, keyboard nav, insert |
| paging | scroll up loads older, anchor stability |
| reconnect | mid-turn drop, resume, duplicate suppression |
| keyboard/insets | mobile web focus, composer covered |
| stale cache | switch back, wrong-last-message window |

## Open items

- C1 cause: asset vs painter (needs one shadow-DOM read of the empty state).
- Session fixtures: `deploy/test/seed.py` seeds files/env/cron/webhooks/profiles but **no
  sessions or messages**, so every chat probe needs its own fixture path. Adding a sessions
  seed is a prerequisite for repeatable chat tests.
