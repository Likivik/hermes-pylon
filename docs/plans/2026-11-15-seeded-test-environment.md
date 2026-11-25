# Test environment with seeded fixtures — implementation plan (v2)

**Goal:** a disposable Hermes instance that the app can be driven against, unattended, with fake-but-fixed data — so uploads, downloads and whole screens can be verified in a real browser, and never touch real data or real credentials.

**Architecture:** a **native systemd service** on this NixOS host, running a throwaway Hermes profile under its own dedicated user, sandboxed with `IPAddressDeny=any` and a data directory the real Hermes home cannot appear inside. A seed script writes fixtures through Hermes' own API/CLI (never by poking SQLite), a verify script asserts fixture counts, and a headless Chrome drives the wasm build at `127.0.0.1:<port>`.

**Why native and not Docker:** both binaries exist here, but the Docker daemon is *inactive*. A systemd unit needs no second runtime, gets the same kernel-level isolation (`ProtectSystem=strict`, `IPAddressDeny`, `PrivateTmp`, `NoNewPrivileges`, its own user), and is one file rather than a compose stack.

**Tech stack:** Hermes Agent pinned to a release tag, systemd hardening directives, Python for seed/verify, Chrome 156 via CDP, existing `pylon-build.sh`.

---

## Current context

- The preview at `127.0.0.1:8080` proxies to the **real** gateway at `9119`. Every manual check today sees real sessions, real keys, real files.
- Goldens are **hand-written state** inside Roborazzi tests. Deterministic, good at visual regressions, and they prove nothing about real data shapes.
- The real defects so far were found by hand: `"unconfigured"` vs `"not_configured"`, rows with no affordance, a Files transport that was a stub. All three would have been caught by a seeded live instance.
- **The wasm picker I shipped this week creates its `<input type="file">` transiently** and removes it in `change()`. CDP's `DOM.setFileInputFiles` needs a stable target, so the upload test cannot drive the current code without an app change. This plan owns that change instead of discovering it later.

## Coverage classes (explicit, because the app has 30 screens)

| Class | What it means | Domains |
| --- | --- | --- |
| **live-seeded** | Real gateway, seeded data, asserted in the browser | Files, Channels, Cron, Webhooks, Sessions, Logs, Bots |
| **live-seeded, pending write API** | Enters live scope only after the capability matrix confirms a write path | Keys, Plugins, Skills, MCP |
| **golden-only** | Roborazzi against hand-written state; no server | all remaining screens |
| **manual** | Needs a third party or a real credential | OAuth round-trips, model calls, provider connect |

Anything not in the first two rows is explicitly *not* covered by this plan, rather than quietly assumed.

## Assumptions

- The app accepts any gateway base URL — it already proxies `8080 → 9119` — so the test port is a config value.
- Hermes' API is stable enough to seed through. Where it is not, the plan says so rather than reaching into `state.db`.

---

## Proposed approach

Three layers with three different jobs. Keeping them separate is what stops this becoming one flaky harness.

1. **Screen goldens (exists).** Roborazzi, pure state. Fast, hermetic, no server. Primary visual gate.
2. **Seeded live instance (new).** Proves data shapes, empty/error/loading transitions and real flows (upload, download, delete, toggle) against a real gateway.
3. **Live checks (new, not screenshots).** DOM and HTTP assertions — row counts, names, hashes, presence of an element. **Not** pixel diffs against Roborazzi: those render through native graphics while Chrome renders through Skia with real font shaping, and relative timestamps ("2 minutes ago") differ between runs. Pixel comparison across the two would fail for reasons that are not bugs.

**Fixture contract:**

- **Idempotent** — seeding twice leaves the same state.
- **Deterministic** — fixed IDs (`seed-file-1`), fixed names, fixed sizes; timestamps fixed where the UI formats them absolutely, avoided in assertions where it formats them relatively.
- **Disposable** — data dir wiped between runs; nothing migrates.
- **Verified** — a count assertion per domain before any test runs; a half-seeded run fails loudly instead of asserting nothing.
- **Fake by construction** — a grep guard fails the seed if any fixture matches `sk-|bot[0-9]{6,}:|[0-9]{9,10}:AA`.

**Fixtures that cannot exist offline** (the honest downgrade):

- Nous provider *connected* → seeds as **disconnected**; connecting is a manual check on `8443`.
- A *running* session → seeds as **finished**; only finished sessions are asserted.
- A *healthy* MCP server → seeds as **failing**; the healthy path stays golden-only.
- Byte-stable relative log timestamps → log assertions check count and level, never the age column.

---

## Step-by-step plan

### Phase 0 — isolation first

1. **Rootless pod, no OS user, no systemd unit.** Everything runs as the invoking user on an internal podman network; `deploy/test/pod.sh up|down|wipe|status`. Verified here: podman 6.1.4, userns `0→976`, a published port on an `--internal` network answers on the host while container egress is blocked.
2. Ports `127.0.0.1:19119` (dashboard) and `127.0.0.1:18080` (app dist), far from the real `9119`/`8080`.
3. Chrome stays on the host, on its own profile — the pod publishes a loopback port, so no browser belongs in the image.
4. Containers: `stub_provider` (loopback `:19200`), `gateway` (`gateway run`, s6-supervised), `dashboard` (`--host 0.0.0.0 --port 9119 --no-open --skip-build`), `dist` (static server for the built app). All share the pod's loopback.
5. Config through `hermes config set` inside the container — never a hand-written `config.yaml`. The fixture password is hashed with the gateway's own `plugins.dashboard_auth.basic.hash_password`.
6. Accept criteria, all verified: dashboard `200` and dist `200` from the host; `curl` to an external host fails inside the containers; `/api/env` is `401` without a session and `200` with one.

### Phase 1 — capability matrix (**done**: see `2026-11-15-capability-matrix.md`)

7. Result: **7 domains are API-seedable** (Files, Keys, Cron, Webhooks, Bots, Skills, MCP), **2 more become seedable through a stub OpenAI-compatible server** (Sessions, Logs — sessions have no create endpoint and logs are read-only, so both are produced by running one conversation against a loopback stub), **Channels is partial** (configure validates a real token), and **Plugins is not seedable offline** (git install needs egress) so it drops to golden-only.
8. The stub server is therefore in scope: ~40 lines of Python on `127.0.0.1:19200`, fixed reply, one config value in the test profile.

### Phase 2 — the seed

9. `deploy/test/seed.py`, one function per domain, each returning the count it wrote; order matters (providers before channels, files before sessions).
10. Fixture set: **Files** `/opt/data/seed` with `notes.md` (1 KB), `diagram.png` (~40 KB), `archive.zip` (~200 KB), one subfolder, one empty folder · **Sessions** 3 finished sessions with fixed titles · **Cron** `daily-digest` enabled, `weekly-report` paused · **Channels** Telegram configured+connected, Slack available, WhatsApp available · **Webhooks** 2 subscriptions with fixed URLs · **Logs** 50 lines at fixed levels · **Bots** `default`, `seed-bot`.
11. `deploy/test/verify.py` asserts the count per domain through the same endpoints the app uses; prints expected vs actual and exits non-zero.

### Phase 3 — point the app at it

12. `deploy/test/preview.sh` runs the proxy with `PYLON_PROXY_TARGET=http://127.0.0.1:19119` and its own cache dir.
13. Manual first pass: sign in with the fixture password, walk every live-seeded screen, confirm content and confirm no real data.
14. Write down every difference from the Roborazzi goldens. Those differences are the output of this phase.

### Phase 4 — the browser harness

15. **App change, owned here:** give the wasm file input a stable `id` (`pylon-file-input`) and keep the node alive until the change event resolves, so `DOM.setFileInputFiles` can target it. Without this the upload test cannot run.
16. One shared auth client (`deploy/test/auth.py`): POST `/auth/password-login`, capture cookies, set them via CDP. Three copies of login logic is how harnesses rot.
17. `Browser.setDownloadBehavior` with a fixed download dir, or the download hash reads an empty folder.
18. First flow that pays for itself: upload a generated 37 KB file → assert the row with its size → download → **assert sha256 equality**. Bytes, not "the row appeared".
19. Then: toggle a channel on and off; pause/resume a cron job; delete a webhook and confirm the row disappears.

### Phase 5 — orchestration

20. `deploy/test/run.sh`: `ram-floor → start → seed → verify → build → preview → browser → down`. A `trap` on EXIT tears down, so a failed run cannot squat the ports. A RAM floor (3 GB, the same threshold `pylon-build.sh` uses) refuses to start and says why — this host has an OOM history.
21. Optional CI job: nightly or label-triggered. It needs a Chrome binary in the runner; keep it off the per-PR path.
22. `deploy/test/README.md` carries the fixture table and the coverage-class table from this document.

---

## Files likely to change

- `deploy/test/` — `pod.sh`, `stub_provider.py`, `seed.py`, `verify.py`, `auth.py`, `browser.py`, `run.sh`, `README.md` (no unit file, no config template: config is set through `hermes config set`).
- `ui/src/wasmJsMain/kotlin/com/m57/hermescontrol/ui/files/FilesPlatformWasm.kt` — stable input `id` (step 15).
- `.github/workflows/` — one optional nightly job.
- No other app code expected; if a base-URL override is missing, that is a config gap to fix in the same PR that needs it.

## Tests and validation

- **Determinism:** seed twice, verify counts identical both times. Assertions are on counts, names and hashes — never on formatted relative times.
- **Isolation:** from inside the service namespace, `/var/lib/hermes` unreadable; no fixture matches the secret-shape grep.
- **Upload/download:** sha256 of downloaded bytes equals sha256 of uploaded file.
- **Teardown:** after a killed run, the ports are free and no process survives.
- **Negative case:** stop the gateway mid-run and confirm the harness fails with a clear message rather than passing on stale data.

## Risks and tradeoffs

- **Seeding through internal APIs is brittle.** Hermes may add fields; the seed needs maintenance. Writing `state.db` directly would break silently instead of loudly — worse.
- **Hardening can bite.** `ProtectHome=yes` and `IPAddressDeny=any` will surface anything the app genuinely needs; each exception must be named, not blanket-allowed.
- **`IPAddressDeny=any` can block loopback on some systemd versions** — verify early, with `IPAddressAllow=localhost`, or the gateway cannot talk to itself and the whole stack looks broken for the wrong reason.
- **Not every screen is seedable.** OAuth and model calls stay manual; the coverage table says so rather than pretending.
- **A fixture password in plain sight is safe only while the service has no real data and no network reach.** Both are load-bearing; if either weakens, the password moves.
- **Image pinning:** `:latest` is refused; pin the repo digest `docker.io/nousresearch/hermes-agent@sha256:1a355ebc8981a4b72a28f4e47e347ba4f0f172b43dc876e5768d175a07c67b22` (3.26 GB, v0.23.1) and bump deliberately. An image ID is not a digest — pulling by `sha256:ed43…` (the image ID) fails.
- **Live findings from the first real runs (2026-11-15):** `hermes gateway start/run` has **no** `--host`/`--port` flags; the app-facing HTTP surface is the **dashboard** (`dashboard --host 0.0.0.0 --port 9119 --no-open --skip-build`), not the gateway's OpenAI-compatible `api_server` (enabled via `API_SERVER_HOST=0.0.0.0` + `API_SERVER_KEY`, which refuses keys shorter than 16 characters). The dashboard **refuses a non-loopback bind until an auth provider is registered** — `dashboard.basic_auth.username` + `password_hash` must be set first; `pod.sh` now hashes the fixture password with the gateway's own helper and configures it before starting the dashboard. Verified from the host: dashboard `200`, dist `200`, `/api/env` `401` without a session and `200` with one, egress blocked inside both containers. **Subsequent full run (same day): green end to end** — `pod.sh up` completed, and `seed.py` + `verify.py` ran unattended with the fixture password: 13 fixtures across 5 domains (`files 3`, `env 3`, `cron 2`, `webhooks 2`, `profiles 2`), exit 0. Login provider id is `basic`; the managed-files root is `/opt/data`, so fixtures live at `/opt/data/seed`; the webhooks platform must be enabled (`POST api/webhooks/enable`) before creating subscriptions.

## Open questions

1. ~~Exact `IPAddressAllow=localhost` syntax on this systemd version~~ — moot: the pod's `--internal` network was verified to block egress while still serving published ports.
2. Does any tested screen need egress? If yes, allowlist that host only.
3. Nightly, or label-triggered on PRs touching `ui/`?
4. Should the live checks publish a JSON report as a CI artifact so a failure is reviewable without a local run?

## Success criteria

- `deploy/test/run.sh` exits 0 on a clean machine with no manual sign-in and no secret in the repo.
- The Files upload/download flow is asserted by sha256, not by eyeball.
- No path from the test stack reaches `/var/lib/hermes` or a real credential.
- The capability matrix exists and the coverage table matches it.
