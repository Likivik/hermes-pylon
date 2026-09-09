#!/usr/bin/env bash
# GitHub Actions build poller for Likivik/hermes-pylon
# Prints a message ONLY when the newest "Build signed APK" workflow run
# completes (and is new since last notify). Empty stdout = nothing delivered.
# Rate-limit safe: 1 API call per tick; state file dedups repeat pings.
set -uo pipefail

# The gateway's own python (absolute path — guarantees availability under cron).
PY=/nix/store/0jj6iw02ppf5ichqhl49d9j0x7ziv76z-hermes-agent-env/bin/python3

TOK=$(grep '^GITHUB_TOKEN=' /var/lib/hermes/.hermes/sops-env | cut -d= -f2 2>/dev/null)
STATE=/var/lib/hermes/.hermes/cron/github-build-poll.state
mkdir -p "$(dirname "$STATE")"

[ -n "$TOK" ] || exit 0

"$PY" - "$TOK" "$STATE" <<'PYEOF'
import sys, json, urllib.request

tok, statefile = sys.argv[1], sys.argv[2]

req = urllib.request.Request(
    "https://api.github.com/repos/Likivik/hermes-pylon/actions/runs?per_page=10",
    headers={"Authorization": f"token {tok}", "Accept": "application/vnd.github+json"},
)
data = json.load(urllib.request.urlopen(req, timeout=30))

last = ""
try:
    with open(statefile) as f:
        last = f.read().strip()
except OSError:
    pass

target = None
for run in data.get("workflow_runs") or []:
    if run.get("name") == "Build signed APK":
        target = run
        break

if not target:
    sys.exit(0)

concl = target.get("conclusion")
rid = str(target["id"])

if target.get("status") == "completed" and concl and rid != last:
    with open(statefile, "w") as f:
        f.write(rid)
    emoji = {"success": "✅", "failure": "❌", "cancelled": "⚠️", "action_required": "🟡"}.get(concl, "🔎")
    print(f"{emoji} hermes-pylon APK build #{rid}: {concl}")
    print(target.get("html_url", "https://github.com/Likivik/hermes-pylon/actions"))
PYEOF
