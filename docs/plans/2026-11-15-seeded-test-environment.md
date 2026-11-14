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

1. Dedicated user `pylon-test`, home `/var/lib/pylon-test`, data at `/var/lib/pylon-test/data`. Nothing test-related lives under `/var/lib/hermes`.
2. Ports `127.0.0.1:19119` (gateway) and `127.0.0.1:18080` (preview), far from the real `9119`/`8080`.
3. Chrome profile at `/var/lib/pylon-test/chrome` — **outside the real home**, which is the point of the whole exercise.
4. `deploy/test/pylon-test.service`: `User=pylon-test`, `ProtectSystem=strict`, `ProtectHome=yes`, `ReadWritePaths=/var/lib/pylon-test`, `PrivateTmp=yes`, `NoNewPrivileges=yes`, `IPAddressDeny=any`, `RestrictAddressFamilies=AF_UNIX AF_INET AF_INET6`. No provider keys in the unit's `Environment`.
5. Pre-write `config.yaml` and `.env` before first boot — the setup wizard is interactive and must never be reached.
6. Accept criterion: from inside the service's namespace, `/var/lib/hermes` is unreadable, and `curl` to any external host fails.

### Phase 1 — capability matrix (**done**: see `2026-11-15-capability-matrix.md`)

7. Result: **7 domains are API-seedable** (Files, Keys, Cron, Webhooks, Bots, Skills, MCP), **2 more become seedable through a stub OpenAI-compatible server** (Sessions, Logs — sessions have no create endpoint and logs are read-only, so both are produced by running one conversation against a loopback stub), **Channels is partial** (configure validates a real token), and **Plugins is not seedable offline** (git install needs egress) so it drops to golden-only.
8. The stub server is therefore in scope: ~40 lines of Python on `127.0.0.1:19200`, fixed reply, one config value in the test profile.

### Phase 2 — the seed

9. `deploy/test/seed.py`, one function per domain, each returning the count it wrote; order matters (providers before channels, files before sessions).
10. Fixture set: **Files** `/seed` with `notes.md` (1 KB), `diagram.png` (~40 KB), `archive.zip` (~200 KB), one subfolder, one empty folder · **Sessions** 3 finished sessions with fixed titles · **Cron** `daily-digest` enabled, `weekly-report` paused · **Channels** Telegram configured+connected, Slack available, WhatsApp available · **Webhooks** 2 subscriptions with fixed URLs · **Logs** 50 lines at fixed levels · **Bots** `default`, `seed-bot`.
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

- `deploy/test/` — `pylon-test.service`, `config.yaml.tmpl`, `seed.py`, `verify.py`, `auth.py`, `browser.py`, `run.sh`, `preview.sh`, `README.md`.
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
- **Version pinning:** no image digest to pin, so pin the Hermes release tag in the unit and bump it deliberately.

## Open questions

1. Exact `IPAddressAllow=localhost` syntax on this systemd version — settled in phase 0, not by guessing.
2. Does any tested screen need egress? If yes, allowlist that host only.
3. Nightly, or label-triggered on PRs touching `ui/`?
4. Should the live checks publish a JSON report as a CI artifact so a failure is reviewable without a local run?

## Success criteria

- `deploy/test/run.sh` exits 0 on a clean machine with no manual sign-in and no secret in the repo.
- The Files upload/download flow is asserted by sha256, not by eyeball.
- No path from the test stack reaches `/var/lib/hermes` or a real credential.
- The capability matrix exists and the coverage table matches it.
