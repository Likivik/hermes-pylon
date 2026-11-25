# Profile scoping: closed plan

Status: for approval. No implementation until approved.
Supersedes the P0–P5 sketch; every open question below is answered from source or a live probe.

## Goal

Every request, mutation and socket in the app acts on the profile the user selected — or says plainly
that it cannot — and no screen can claim success while the gateway serves a different profile.

## Measured facts (gateway 0.23.1, commit 3b421578, pod 127.0.0.1:19119)

| Fact | Answer | Source |
|---|---|---|
| List rows carry `runtime_revision`/`runtime_generation` | yes | live `GET /api/sessions` |
| Detail row carries them | yes | live `GET /api/sessions/{id}` |
| Search hits carry them | **no** | live `GET /api/sessions/search` |
| `GET .../latest-descendant` | returns ids + path only, no row | `web_routers/sessions.py:602` |
| `DELETE /api/sessions/{id}` | fenced: `request_id`+`expected_revision`(+`expected_generation`), `profile` query | `sessions.py:853` |
| PATCH | same fence | `sessions.py:933` |
| `bulk-delete`, `prune` | `profile` in body, no fence, `destructive_profile` | `sessions.py:466,985`, `_common.py:62` |
| Omitted profile on destructive route, multiplex host | **400** | `_common.py:77-84` |
| Missing fence | 409 `client_upgrade_required` (none sent) / `mutation_identity_required` (partial) | `web_server_sessions.py:287-293` |
| Retry with the same `request_id` | **idempotent** — a receipt authorizes the original principal's exact retry; the transaction still verifies digest and epoch | `gateway/session_mutations.py:51` |
| Stale revision status | **unverified** — source routes a delete fence failure through `RuntimeStoreError('storage_unavailable')` → **503** | `session_mutations.py:454`, `web_server_sessions.py:267` |
| `active` vs `current` | `active` = stored preference; `current` = process-pinned, never follows (60 s, restart) | earlier probe |
| Socket scope | `profile` at handshake (`chat_ws.py:249,499`) | source |

## Invariants

1. One owner of identity. `ProfileRepository` owns `selected`, the client generation, and
   `lastObservedCurrent` + timestamp. Nothing else fetches or caches them.
2. State, not events. Consumers converge on the current value; no counters, no `drop(1)`.
3. Scope rides on the transport: profile is part of the client generation; only the repository calls
   `setSelectedProfile`.
4. Seams, not singletons: ViewModels take interfaces; any screen constructs in a unit test with no network.
5. Fence comes from observed data. Never guess. Never hide a request: a resolve step is explicit and visible.
6. Routes are classified *scoped* / *body-scoped* / *unscoped* / *socket*. Unscoped mutations never rely on
   the server's pinned profile; the client always names one, and refuses locally when it cannot.
7. Errors are typed once at the transport seam; UI renders the type, never a substring.
8. Row badge = intent (selection). The serving fact is stated once per screen.

## Answers to the four holes

**H1 — `request_id` retry semantics.** Settled: retry keeps the same `request_id`. A receipt makes it the
original principal's exact retry, and the transaction still verifies digest and epoch, so a retry after a
lost response cannot double-apply. A *new* `request_id` against a stale revision fails the fence — which is
correct: that is a different attempt, not a repeat. Spec: never regenerate `request_id` for a retry of the
same user action; regenerate on a new user action.

**H2 — confirm-fetch dialog.** The resolve step happens at **confirm**, not at open, and is visible:

- `requestDeleteSession(id)` sets `resolving = id` and disables confirm.
- Dialog body: "Checking this session…" with the confirm button disabled; no spinner-only state.
- Row has a revision (list, detail, pinned) → confirm enables immediately, no request.
- Row is a search hit (never has a revision) → on confirm: `GET /api/sessions/{id}?profile=…`, bounded to
  5 s, single attempt, single-flight per id.
- Fetch 200 → fence built, mutation sent.
- Fetch 404 → close the dialog, say "already deleted", reload the list. **This is the common race.**
- Fetch 401 → session expired; global re-auth path.
- Fetch timeout/5xx → keep the dialog open with "Could not check this session — retry", confirm enabled.
- Never re-fetch on retry of the same action; reuse the fence already in hand.

**H3 — profile deleted server-side.** On `ProfileNotFound` (404 `Profile 'x' does not exist`) from any scoped
request: clear the selection, re-read `/api/profiles/active`, adopt `active` if it exists, tell the user
"Profile X no longer exists on this gateway — switched to Y". If `active` also fails to exist, land on the
Profiles screen with an empty selection and no auto-retry. Exactly one hop, never a loop.

**H4 — two wasm tabs.** Decision: **last write wins, and each tab is told.**
- Selection persists per gateway instance in `localStorage` (as today).
- Each tab listens to the `storage` event for the selection key and adopts the new selection without a
  reload, showing "Profile changed in another tab — now browsing X".
- In-flight mutations in a stale tab fail the fence or the profile check, which is the correct outcome; no
  attempt to merge concurrent edits.
- Rationale: the gateway is the arbiter; a tab is a viewer, not an owner.

## Phases and acceptance criteria

**P0 — seam, done.**
Acceptance: `:ui:compileKotlinJvm` green; `SlashCommandDispatchRpcTest`, `ChatViewModelTest`,
`SessionsViewModelTest`, `SessionSearchCorrectnessTest` green from a cold daemon; no test constructs a
network client; PR #209 CI green.

**P1 — confirm-fetch and realistic fixtures.**
Acceptance: delete works from list, from search hit, and from pin hydration; a 404-during-confirm closes the
dialog and reloads; no hidden request outside the confirm step; the existing assertions in
`SessionSearchCorrectnessTest:251` are unchanged and pass.

**P2 — request_id + error taxonomy.**
Acceptance: a documented spec in `docs/profile-scoping.md` with the retry rule; transport maps 401/404/409
(`client_upgrade_required`, `mutation_identity_required`)/503 into typed errors; a stale revision is
identifiable — verified against the pod with a deliberate stale write on a throwaway session; no UI string
is parsed.

**P3 — route classification + CI lint. DONE.**
`tools/route_scopes.json` records all 331 routes (201 query / 7 body / 123 none) generated from the
gateway's routers by `scripts/generate_route_scopes.py`; plugin routes classified by probe (unknown
profile → 404). `scripts/check_route_scope.py` fails CI on a client path the inventory does not know and
on a body-scoped route whose request model carries no profile. Red-proofed: 5 unittest cases, including
one that must fail. Acceptance met.

**P4 — narrow repository.**
Acceptance: no ViewModel references `ApiClient` for identity; `selected` and `lastObservedCurrent` are the
only sources; switching a gateway clears and re-resolves in one place; the existing behaviour tests pass
unchanged.

**P5 — UI truth.**
Acceptance: row badge follows selection; serving-vs-selected stated once per screen; `client_upgrade_required`
asserts in dev builds and never reaches user copy.

## Risks

- Stale-revision status is unverified (409 vs 503). P2 must measure it before P2's mapping is called done.
- Search hits cannot be fenced; the confirm step is the only correct place to resolve them.
- Bulk-delete and prune are unfenced by protocol: a confirm must state the count, and the divergence window
  is a server-side issue to file, not a client fix.
- Multi-tab is last-write-wins by decision, not by accident.

## Explicitly out of scope

TTL revalidation (polling cannot fix a server-side divergence); `serving` as an owned flow; renames of the
Profiles screen; anything about `/api/fs`.
