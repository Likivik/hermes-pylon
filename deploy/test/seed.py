#!/usr/bin/env python3
"""Fill the test Hermes with fake-but-fixed data.

Every write goes through the same HTTP endpoint the app uses, so a change in the
gateway's API breaks the seed loudly instead of silently rotting.

    PYLON_TEST_BASE=http://127.0.0.1:19119 PYLON_TEST_TOKEN=... python3 seed.py

Each function returns how many records it wrote; `verify.py` asserts those counts.
"""

from __future__ import annotations

import base64
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request

BASE = os.environ.get("PYLON_TEST_BASE", "http://127.0.0.1:19119").rstrip("/")
TOKEN = os.environ.get("PYLON_TEST_TOKEN", "")
TIMEOUT = 15

# Fixed content: same names, same sizes, every run.
FIXTURES_DIR = "/seed"
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
        with urllib.request.urlopen(request, timeout=TIMEOUT) as response:
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
        require(
            "POST",
            "api/files/upload",
            {
                "path": f"{FIXTURES_DIR}/{name}",
                # The app uploads through the same field; base64 keeps this text-only.
                "content": base64.b64encode(content).decode(),
                "encoding": "base64",
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


SEEDS = {
    "files": seed_files,
    "env": seed_env,
    "cron": seed_cron,
    "webhooks": seed_webhooks,
    "profiles": seed_profiles,
}


def main() -> None:
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
