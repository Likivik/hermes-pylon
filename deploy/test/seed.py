#!/usr/bin/env python3
"""Fill the test Hermes with fake-but-fixed data.

Every write goes through the same HTTP endpoint the app uses, so a change in the
gateway's API breaks the seed loudly instead of silently rotting.

    PYLON_TEST_BASE=http://127.0.0.1:19119 PYLON_TEST_TOKEN=... python3 seed.py

Each function returns how many records it wrote; `verify.py` asserts those counts.
"""

from __future__ import annotations

import base64
import http.cookiejar
import json
import os
import shlex
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request

BASE = os.environ.get("PYLON_TEST_BASE", "http://127.0.0.1:19119").rstrip("/")
# Needed only by `sessions`: turns are run inside the pod, through the gateway itself.
POD = os.environ.get("PYLON_TEST_POD", "")
TOKEN = os.environ.get("PYLON_TEST_TOKEN", "")
TIMEOUT = 15

# The harness signs in the way a human would: the fixture password against the
# registered `basic` provider. No token extraction, no manual step.
LOGIN_USER = os.environ.get("PYLON_TEST_USER", "seed")
# No default on purpose: pod.sh generates a password per run and exports it.
# A fallback here would quietly ship a known credential.
LOGIN_PASSWORD = os.environ.get("PYLON_TEST_PASSWORD")
JAR = http.cookiejar.CookieJar()
OPENER = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(JAR))


def login() -> None:
    if not LOGIN_PASSWORD:
        raise SystemExit("PYLON_TEST_PASSWORD is not set; pod.sh generates it per run")
    body = json.dumps({"provider": "basic", "username": LOGIN_USER, "password": LOGIN_PASSWORD}).encode()
    request = urllib.request.Request(f"{BASE}/auth/password-login", data=body, method="POST")
    request.add_header("content-type", "application/json")
    try:
        with OPENER.open(request, timeout=TIMEOUT) as response:
            if response.status >= 300:
                raise SystemExit(f"login failed: {response.status}")
    except urllib.error.HTTPError as error:
        raise SystemExit(f"login failed: {error.code} {error.read().decode(errors='replace')[:200]}") from error
    if not JAR:
        raise SystemExit("login returned no session cookies")

# Fixed content: same names, same sizes, every run.
FIXTURES_DIR = "/opt/data/seed"
FIXTURE_FILES = {
    "notes.md": b"# Seeded notes\n\nFixed content for the Files screen.\n" * 20,
    "diagram.png": b"\x89PNG\r\n\x1a\n" + b"seed-fixture" * 2500,
    "archive.zip": b"PK\x05\x06" + b"\x00" * 18 + b"seed" * 5000,
}


def call(method: str, path: str, body: dict | None = None) -> tuple[int, object]:
    url = f"{BASE}/{path.lstrip('/')}"
    data = json.dumps(body).encode() if body is not None else None
    request = urllib.request.Request(url, data=data, method=method)
    request.add_header("content-type", "application/json")
    if TOKEN:
        request.add_header("authorization", f"Bearer {TOKEN}")
    try:
        with OPENER.open(request, timeout=TIMEOUT) as response:
            raw = response.read()
            return response.status, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as error:
        return error.code, error.read().decode(errors="replace")
    except urllib.error.URLError as error:
        raise SystemExit(f"cannot reach {url}: {error.reason}") from error


def require(method: str, path: str, body: dict | None = None) -> object:
    status, payload = call(method, path, body)
    if status >= 300:
        raise SystemExit(f"{method} {path} -> {status}: {payload}")
    return payload


def seed_files() -> int:
    require("POST", "api/files/mkdir", {"path": FIXTURES_DIR})
    require("POST", "api/files/mkdir", {"path": f"{FIXTURES_DIR}/empty"})
    for name, content in FIXTURE_FILES.items():
        mime = "image/png" if name.endswith(".png") else "application/zip" if name.endswith(".zip") else "text/markdown"
        require(
            "POST",
            "api/files/upload",
            {
                "path": f"{FIXTURES_DIR}/{name}",
                # The app sends a data URL; base64 keeps this text-only.
                "data_url": f"data:{mime};base64,{base64.b64encode(content).decode()}",
                "overwrite": True,
            },
        )
    return len(FIXTURE_FILES)


def seed_env() -> int:
    variables = {
        "OPENROUTER_API_KEY": "seed-not-a-real-key",
        "ANTHROPIC_API_KEY": "seed-not-a-real-key",
        "TELEGRAM_BOT_TOKEN": "seed-not-a-real-token",
    }
    for name, value in variables.items():
        require("PUT", "api/env", {"key": name, "value": value})
    return len(variables)


def seed_cron() -> int:
    jobs = [
        {
            "name": "daily-digest",
            "schedule": "0 9 * * *",
            "prompt": "Summarise yesterday.",
            "enabled": True,
        },
        {
            "name": "weekly-report",
            "schedule": "0 9 * * 1",
            "prompt": "Write the weekly report.",
            "enabled": False,
        },
    ]
    for job in jobs:
        require("POST", "api/cron/jobs", job)
    return len(jobs)


def seed_webhooks() -> int:
    # The platform ships disabled; the app enables it from the Webhooks page.
    require("POST", "api/webhooks/enable", {"enabled": True})
    hooks = [
        {"name": "seed-message", "url": "https://example.test/hook", "events": ["message.created"]},
        {"name": "seed-session", "url": "https://example.test/done", "events": ["session.finished"]},
    ]
    for hook in hooks:
        require("POST", "api/webhooks", hook)
    return len(hooks)


def seed_profiles() -> int:
    profiles = [{"name": "default"}, {"name": "seed-bot"}]
    for profile in profiles:
        # A duplicate is fine: the point is that the profile exists afterwards.
        status, _ = call("POST", "api/profiles", profile)
        if status >= 300 and status != 409:
            raise SystemExit(f"POST api/profiles {profile['name']} -> {status}")
    return len(profiles)


def seed_sessions() -> int:
    """Two chat sessions with real message shapes, created by running real turns.

    Idempotent by a marker file inside the pod: the ids of the sessions this function created
    are recorded, and a run is a no-op while both still exist. Turns go through the gateway with
    the scripted provider, so the message shapes are the gateway's, not ours.
    """
    if not POD:
        raise SystemExit("seed sessions needs PYLON_TEST_POD=<pod name>")
    state_path = "/opt/data/pylon_seed_sessions.json"
    recorded: list[str] = []
    out = subprocess.run(["podman", "exec", f"{POD}-gateway", "cat", state_path], capture_output=True, text=True)
    if out.returncode == 0 and out.stdout.strip():
        try:
            recorded = json.loads(out.stdout)
        except json.JSONDecodeError:
            recorded = []
    alive = [sid for sid in recorded if call("GET", f"api/sessions/{sid}")[0] == 200]
    if len(alive) == 2:
        print(f"sessions already seeded: {alive[0][:8]}, {alive[1][:8]}", flush=True)
        return 2

    prompts = ["Pylon fixture: markdown", "Pylon fixture: long tool"]
    for prompt in prompts:
        result = subprocess.run(
            ["podman", "exec", f"{POD}-gateway", "sh", "-c", f"timeout 240 hermes -z {shlex.quote(prompt)}"],
            capture_output=True,
            text=True,
            timeout=300,
        )
        if result.returncode != 0:
            raise SystemExit(f"turn failed for {prompt!r}: {result.stderr[-400:]}")

    status, body = call("GET", "api/sessions?profile=default&limit=2")
    if status != 200 or not isinstance(body, dict):
        raise SystemExit(f"cannot list sessions after seeding: {status}")
    ids = [row["id"] for row in body.get("sessions", [])][:2]
    if len(ids) != 2:
        raise SystemExit(f"expected 2 sessions after seeding, saw {len(ids)}")
    subprocess.run(
        ["podman", "exec", "-i", f"{POD}-gateway", "sh", "-c", f"cat > {state_path}"],
        input=json.dumps(ids),
        text=True,
        check=True,
    )
    return len(ids)


SEEDS = {
    "files": seed_files,
    "env": seed_env,
    "cron": seed_cron,
    "webhooks": seed_webhooks,
    "profiles": seed_profiles,
    "sessions": seed_sessions,
}


def main() -> None:
    if not TOKEN:
        login()
    wanted = sys.argv[1:] or list(SEEDS)
    counts: dict[str, int] = {}
    for name in wanted:
        if name not in SEEDS:
            raise SystemExit(f"unknown domain {name}; known: {', '.join(SEEDS)}")
        counts[name] = SEEDS[name]()
        print(f"seeded {name}: {counts[name]}", flush=True)
    print("counts: " + json.dumps(counts), flush=True)


if __name__ == "__main__":
    main()
