# Profile scoping

How the app decides which Hermes profile a request belongs to, and why it never guesses.

## Terms

| Term | Meaning |
| --- | --- |
| **Gateway instance** | Which server the app is connected to (`GatewayInstance`, `selectedGatewayId`). |
| **Selected profile** | The profile the app browses. Owned by the client, persisted per instance. |
| **Activated profile** | The profile the gateway *process* serves (API `current`). Read-only, decided by the gateway. |

API mapping, in one place (`ApiClient`, `ProfilesViewModel`): `active` → selection; `current` → activated.

## What the gateway actually does

Measured against 0.23.1 with two real profiles:

```
POST /api/profiles/active {"name":"probe2"}  -> 200 {"ok":true,"active":"probe2"}
GET  /api/profiles/active                    -> {"active":"probe2","current":"default"}
     current unchanged for 60 s, and still unchanged after POST /api/gateway/restart
GET  /api/sessions            (no param)     -> the process profile's rows
GET  /api/sessions?profile=probe2            -> probe2's rows
GET  /api/sessions?profile=nope              -> 404 "Profile 'nope' does not exist."
```

So: `active` is a preference that other processes read later. `current` is this process's own
profile. **Only an explicit `profile=` parameter scopes data**, and a switch writes a preference
without moving this process.

## Design: the profile is part of the client generation

`ApiClient` builds one `HttpClient` per generation. `ApiClient.setSelectedProfile(name)` stores the
selection, rebuilds the client, and appends `profile=<name>` to every request
(`HttpClient.withProfileScope`, `HermesHttpClient.kt`). A call site cannot forget the parameter,
because call sites never add it.

Consequences:

- Every request is scoped, including ones nobody remembered were profile-owned.
- A response from the previous profile cannot arrive in the new one — the old client is gone.
- Global routes ignore the parameter; profile-owned routes act on it.
- Switching costs one client rebuild, which the app already did for gateway changes.

## Honest state, not optimistic state

Selecting a profile:

1. Rebuild the client with the new profile → the app now reads that profile's data.
2. `POST /api/profiles/active` to record the preference.
3. Re-read `active`/`current`.
4. Report success **only** when `current == selected`. Otherwise the screen says
   *"Browsing X — the gateway still serves Y"* and keeps the pending state visible.

No toast claims a switch the gateway did not make, and nothing restarts the gateway: a restart
drops live sockets and in-flight turns, and (measured) does not even adopt the setting.

## Session mutations carry a fence

`PATCH /api/sessions/{id}` and other mutations require `request_id`, `expected_revision` and
`expected_generation`; without them the gateway answers `409 client_upgrade_required`, and with a
stale revision it reports a conflict. `SessionFence` builds them from the row the user saw, so a
write can never silently land on state that changed underneath. The profile travels in the **body**
of those requests, not the query.

## Files is out of scope, deliberately

`/api/files` browses the gateway process's own home (`locked_root`, `can_change_path: false`), so a
profile switch cannot move it. `/api/fs` accepts `profile`, but it is sandboxed (relative reads are
`403`), has no upload/mkdir/delete, and returned identical listings for two profiles on a host-local
install. Files therefore shows a fixed provenance note instead of pretending to be per-profile.

## Failure semantics

A `409` means two different things and they are reported differently:

- `client_upgrade_required` / `mutation_identity_required` — the app and gateway disagree about how a
  session write is fenced. Nothing was written. The message says to reopen the session.
- a stale `expected_revision` — another writer got there first. The message says the session changed
  on another device and the list reloads.

A mutation with no known profile is refused locally rather than sent: on a multi-profile host the
gateway answers `400`, and guessing would write to the wrong store.

## Limits worth knowing

- The inventory is version-specific: 0.23.1 has 340 routes, 192 accepting `profile`. Re-run the
  audit when the gateway version changes.
- Chat websockets are scoped at handshake (`chat_ws.py:249,499`): the URL carries `profile=` and the
  socket is re-opened when the selection changes. A reconnect during a live turn loses that turn's
  stream — the app reopens only when the socket is already connected, so a cold start never flaps.
- A future cross-profile view needs a second, deliberate client — never a per-call parameter.

## Retry and refusal semantics (measured, gateway 0.23.1)

**Retrying a mutation.** Reuse the same `request_id`. The gateway stores a receipt and treats the
retry as the original principal's exact repeat, re-verifying the digest and epoch inside the
transaction — so a retry after a lost response cannot double-apply. A *new* `request_id` against a
stale revision fails the fence, which is correct: it is a different attempt, not a repeat.
Rule: same `request_id` for a retry of the same user action; a new one for a new action.

**Refusals.** The 409 family carries four meanings and they are never rendered by parsing a string:

| status | `detail` | kind | what the UI says |
|---|---|---|---|
| 409 | `revision_conflict` | `StaleRevision` | changed on another device — refreshing |
| 409 | `client_upgrade_required` | `FenceMissing` | app and gateway disagree — reopen the session |
| 409 | `mutation_identity_required` | `FenceMissing` | same |
| 409 | `session_busy` | `Busy` | session is busy — try again |
| 400 | `… requires an explicit profile …` | `ProfileRequired` | needs a profile: this gateway serves several |
| 404 | — | `SessionGone` | already gone |
| 401 | — | `Unauthorized` | session expired — sign in again |

`FenceMissing` is a **client bug**, not a user error: it asserts in debug builds and only degrades to
copy in release. `StaleRevision` is the only kind that triggers a reload.

Measured: a PATCH with the fence in the body and a stale `expected_revision` returns
`409 {"detail":"revision_conflict"}`; the same mismatch on DELETE (fence in the query) returns the
same. Nothing is written in either case.

## Route classification

`tools/route_scopes.json` is generated from the gateway's own routers and records, per route, how it
learns the profile:

- `query` — a `profile` parameter on the endpoint (201 routes)
- `body` — the profile arrives in the request model (`destructive_profile(...)` or `body.profile`): 7 routes
- `none` — the route takes no profile at all (123 routes)

Plugin-owned routes (`/api/plugins/...`) are not in `web_routers`; they are classified by probe — an
unknown profile returns 404, so they are `query` — and are preserved across regeneration.

`scripts/check_route_scope.py` runs in CI and fails when:

- the client calls a path the inventory does not know (drift: new endpoint or renamed route), or
- a function calls a **body-scoped** route and sends a model whose constructor has no `profile`.

Regenerate after upgrading the gateway:

```
python3 scripts/generate_route_scopes.py /path/to/hermes_cli
```

Classification is measured, not asserted: the generator reads each handler's own span (by indentation)
and looks for the profile in its signature, in a `destructive_profile(...)` call, or in an attribute read
off the request model. A fixed character window was tried first and misclassified routes in both
directions — borrowing a neighbour's call and cutting off long handlers.
