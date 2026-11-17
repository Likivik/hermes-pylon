# Rail REST source + reconciliation plan (2026-11-17)

## Problem

The chat rail folds sessions into `old·N` on `started_at` (creation), not last use: a
conversation created 30 days ago but used this morning folds as stale and sorts at the
bottom. The gateway's `session.list` RPC does not ship a last-activity field — but the
REST route the dashboard uses does, on the very same DB rows.

## Verified facts

Verified against the **running gateway's own source** (nix store
`hermes-agent-0.23.1-patched`) and the pylon workspace:

1. `tui_gateway/methods_session.py` `_session_row_summary` builds the `session.list`
   row from `id, title, preview, started_at, message_count, source, _lineage_root_id` —
   **no `last_active`**. The DB row it reads *has* it: `sessions.last_activity_at`
   (live SQL: populated, ≈ started_at + turn duration), and `_listing_rows` already
   orders by `COALESCE(last_activity_at, started_at) DESC`.
2. `hermes_cli/web_routers/sessions.py` `GET /api/sessions` returns
   `db.list_sessions_rich(...)` rows **as-is** — `last_active` included — and
   `order=recent` is exactly that sort. `methods_profiles.py` and `methods_projects.py`
   read `last_active` from the same row shape.
3. Route caps `limit` at 100 (`Query(le=100)`); accepts `profile`,
   `exclude_sources`, `sources`, `archived`; **`GET /api/sessions?limit=500` → 422**.
   `ChatViewModel.kt:3058` (`fetchServerMessageCount`) calls `limit=500` → that call
   fails every time today and silently falls through. Verified statically; live
   confirmation pending (slice D).
4. Pylon: `SessionUi.lastActive` is mapped from `raw["started_at"]`
   (`RailSessions.kt:28`); the decoder runs `ignoreUnknownKeys = true`, so a
   `last_active` field would be discarded even if the server sent it.
5. `HermesGatewayApi.getSessions` has **no `profile` parameter**, but the route reads
   one (`_open_session_db_for_profile`). The WS socket is per-profile; REST is not.
   A multi-profile user would get the wrong rail. Must pass `selectedProfileId()`.
6. Auth: REST works on both shipping targets with what the app already has — browser
   cookies on web, OkHttp `PersistentCookieJar` on Android (`HermesHttpClient.kt:126`).
   `ChatViewModel` already calls `ApiClient.hermesApi` REST today.
7. Rail grouping (`RailGrouping.kt`): pinned first, fresh middle, stale (>7 d) folded
   into `old·N`; unknown (`lastActive == 0`) counts fresh. Pin ids are client state
   resolved via `getSession`/`getSessionLatestDescendant` (`SessionPinning.kt`).
8. **Unverified (needs a logged-in browser):** live payload equivalence between
   `GET /api/sessions` and the WS `session.list` on the same profile (ids, counts,
   deny-list/subagent scope), and `last_active` present in every row. Attempted this
   session; both app tabs had navigated away.

## Design

Industry practice (TanStack Query semantics) applied to what upstream's
`ChatSessionList.tsx` already proves works, hardened where it is weak:

- **Single source of truth.** REST rows are the rail catalog. The WS `session.list`
  reply is demoted to an **invalidation signal** — its rows are discarded. There is
  never a second ordered copy to merge; "reconciliation" reduces to
  newest-response-wins.
- **Request token.** A monotonic sequence; only the newest request may commit state.
  A slow response after a refresh cannot overwrite a newer list (upstream has this;
  Pylon has only the `railRefreshing` spinner).
- **Dedupe + debounce.** In-flight fetch coalesces later invalidations; WS bursts
  trigger at most one fetch per ~3 s, minimum interval ~10 s.
- **Stale-while-revalidate on failure.** On error keep the last-good list, log the
  failure category, set `railStale` past 60 s. Never blank the rail.
- **Backoff.** Upstream polls a dead gateway every 20 s. Double the interval per
  consecutive failure up to 5 min; reset on success.
- **Lifecycle.** Refresh on foreground/visibility, not in background. Web:
  `visibilitychange`; Android: `ON_START`/`ON_STOP` — an expect/actual seam with a
  documented contract, per the §3.1 capability-seam style.
- **Pagination without silent truncation.** Page 1 at `limit=100`; if
  `total > fetched`, page up to a 300 cap, then log. The RPC shipped up to 200 today;
  a quiet 100-row rail would be a regression.
- **Overlays stay separate.** Running badge, optimistic rename/pin/delete remain
  their own state composed at render; REST replaces only the catalog.
- **"Old" definition.** Fold on `last_active ?: started_at` (label it *last
  activity* — it is an activity heartbeat, not strictly the last user message).
  Missing timestamp stays fresh. Pinned never fold. A gateway `last_message_at`
  distinction is an upstream follow-up, not ours.
- **Diagnostics.** Log refresh outcome, row counts, page count, duration, error
  category. No URLs with query secrets (cookie auth keeps it that way). The
  `rail hid N automation sessions` log moves with the filter.

## Slices

- **A — model + mapper (shippable alone).** `SessionInfo.last_active: Double? = null`;
  `railSessionOf(info: SessionInfo)` overload; fold prefers `last_active`;
  keep server order; cron filter on the new type (server `exclude_sources` +
  client id-shape fallback both stay). Tests: decode + absent-field fallback, fold
  boundary at exactly 7 d, pinned never fold, unknown = fresh.
- **B — transport + reconciliation.** `profile` param wired from
  `selectedProfileId()`; request token; dedupe/debounce; backoff; SWR-keep;
  pagination; WS reply demoted to signal; `loadSessions()` rewired. Tests: stale
  response loses the race, failure keeps last-good, pagination dedupe, profile
  switch refetch. Red-proof the token guard with a deliberately stale response.
- **C — the `limit=500` fix.** Replace the whole listing with
  `getSession(sessionId)` — one precise row instead of a 500-row list that 422s.
- **D — verification.** Live payload diff REST-vs-WS on the two-profile pod fixture
  (fact 8); rebuild bundle; phone check (rail order + fold); CI.

## Non-goals

- The gateway RPC one-liner (`last_active` in `_session_row_summary`) — file
  upstream separately; this plan removes our dependency on it.
- Pinning UI, ETag/conditional GET (route does not support it), a local boot cache.

## Risks

- **Profile mismatch** — the highest-value fix in B; verify against the two-profile
  fixture before merging.
- **`total` semantics** — confirm `total` counts the same scope as rows
  (`exclude_children=True` in the route) before trusting it for pagination.
- **wasm vs OkHttp query encoding** — extend `HermesGatewayApiTest` for the new
  query params on both engines.
- **Ratchet** — any new silent catch here must be classified or the CI budget bites.

## Open question (one decision)

Tests in this PR, or a separate tests PR first? Recent precedent (#200) shipped
`RailSessionsTest` with the implementation and you approved it; the older standing
rule said separate. Plan assumes **same PR** unless you say otherwise.

## Implementation status (2026-11-17)

- **A — model + mapper: DONE** (#202). `SessionInfo.last_active`; `railSessionOf` /
  `railSessionsOf`; fold axis `last_active ?: started_at`; the raw-map mapper prefers
  the same field. Five REST-row tests, including the inversion case that would pass on
  the old code only by accident.
- **B — transport + reconciliation: DONE** (#202). REST is the source; the WS
  `session.list` reply is a signal; monotonic request token; mid-flight forced refresh
  coalesced (a dropped one left the spinner stuck); failure keeps last-good rows with
  10 s → 5 min backoff; `ON_START` refresh; pagination 100/page to 300; `profile`
  passed from `selectedProfileId()`.
- **C — the `limit=500` defect: DONE** (#202). Now `getSession(sessionId)`.
- **D — verification: PARTIAL, device half confirmed.** Bundle rebuilt from
  `main@origin` at 20:51 and served (`ca3d6d91…wasm`, 12,272,191 B, HTTP 200); the rail
  was then checked on the phone and **confirmed working** — order and `old·N` fold both
  behave. Still open: the live REST-vs-WS payload diff on the two-profile fixture,
  blocked because the preview browser sessions were logged out (logging in is not
  something the assistant does) and the test pod was stopped, so that fixture is not
  currently running.

**Deviations from the plan, stated rather than hidden:** the *rail stale* indicator was
dropped (P2) — failures are logged with their backoff, but no visual flag exists; and no
polling timer was added, because the socket already supplies invalidation.

**Follow-up queued, not done:** the gateway one-liner
(`"last_active": tip_row.get("last_activity_at") or started_at` in
`_session_row_summary`) is still the correct fix upstream — this plan only removes our
dependency on it.
