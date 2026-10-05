# CI build notifications (Erebus · Hermes cron)

How `hermes-pylon` APK builds notify Erebus when they finish.

## Why

`notify_on_complete` on local background processes wakes the *agent*, not the
human — it never sends a native Telegram push. GitHub only pushes on failure by
default, not success. To get a guaranteed ping on **both** success and failure,
Erebus polls the Actions API via a Hermes cron job.

## The poller

`scripts/github-build-poll.sh` — a `no_agent` Hermes cron script. It queries the
newest "Build signed APK" workflow run each tick and prints a message **only when
a run *completes* and is new since the last notify** (tracked in
`/var/lib/hermes/.hermes/cron/github-build-poll.state`). Empty stdout = silent.

## Cron shape (bounded per build — not an infinite poller)

Because a build only ever starts when an `apk-*` tag is pushed (from the Erebus
session), the cron is created **per build**, not forever:

| Param | Value |
|---|---|
| schedule | `every 1m` |
| repeat | `20` (≈ 20 min window; a build takes ~8–13 min) |
| deliver | `origin` (this chat) |
| no_agent | true |
| script | `github-build-poll.sh` |

On the tick where the build completes, the script pings once and records the run
id; every later tick is silent until the **next** build has a new run id — i.e.
"stop on success/fail" without an infinite poller.

## Rate limit

Exactly **1 GitHub API call per tick**; 20 ticks « the 5,000/hr authenticated
limit. The script uses the fine-grained `GITHUB_TOKEN` from hermes `sops-env`
(valid for read-only Actions queries).

## Procedure

1. Push an `apk-*` tag to trigger `apk.yml`
2. Create the bounded cron (shape above)
3. The poller pings this chat on success/failure
4. After the window (~20 min) the cron stops; create a fresh one on the next tag

## Gotchas

- **Dependency breakage:** adding a dependency (e.g. a JitPack lib) can fail the
  CI "Build APK" step before signing — the poller catches that and reports the
  failed run, so always read the run status before treating a tag as shipped.
- The script's python path is pinned to the Erebus gateway env; update it if the
  gateway env store path changes (`/nix/store/<...>-hermes-agent-env`).
