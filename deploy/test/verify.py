#!/usr/bin/env python3
"""Assert the seed actually landed, before any browser test runs.

A half-seeded instance must fail here, loudly, rather than let a test assert nothing
and pass. Counts come from the same endpoints the app calls.

    PYLON_TEST_BASE=http://127.0.0.1:19119 PYLON_TEST_TOKEN=... python3 verify.py
"""

from __future__ import annotations

import os
import sys

from seed import FIXTURE_FILES, FIXTURES_DIR, call, login

EXPECTED = {
    "files": len(FIXTURE_FILES),
    "env": 3,
    "cron": 2,
    "webhooks": 2,
    "profiles": 2,
}


def count_files() -> int:
    _, payload = call("GET", f"api/files?path={FIXTURES_DIR}")
    if not isinstance(payload, dict):
        return -1
    entries = payload.get("entries") or payload.get("files") or []
    return sum(1 for entry in entries if not (entry.get("is_directory") or entry.get("isDirectory")) and entry.get("name") in FIXTURE_FILES)


FIXTURE_ENV = {"OPENROUTER_API_KEY", "ANTHROPIC_API_KEY", "TELEGRAM_BOT_TOKEN"}


def count_env() -> int:
    # /api/env returns every variable in the container (392 of them); count only ours.
    _, payload = call("GET", "api/env")
    if isinstance(payload, dict):
        return sum(1 for name in FIXTURE_ENV if name in payload)
    return -1


def count_cron() -> int:
    _, payload = call("GET", "api/cron/jobs")
    jobs = payload.get("jobs") if isinstance(payload, dict) else payload
    return len(jobs or [])


def count_webhooks() -> int:
    _, payload = call("GET", "api/webhooks")
    hooks = payload.get("subscriptions") or payload.get("webhooks") if isinstance(payload, dict) else payload
    return len(hooks or [])


def count_profiles() -> int:
    _, payload = call("GET", "api/profiles")
    profiles = payload.get("profiles") if isinstance(payload, dict) else payload
    return len(profiles or [])


def main() -> None:
    checks = {
        "files": count_files,
        "env": count_env,
        "cron": count_cron,
        "webhooks": count_webhooks,
        "profiles": count_profiles,
    }
    login()
    failures = []
    seen: dict[str, int] = {}
    for name, expected in EXPECTED.items():
        actual = checks[name]()
        seen[name] = actual
        mark = "ok" if actual == expected else "MISMATCH"
        print(f"{name:10} expected {expected:2} actual {actual:2}  {mark}", flush=True)
        if actual != expected:
            failures.append(name)

    if failures:
        print(f"FAILED: {', '.join(failures)}", flush=True)
        sys.exit(1)
    print("seed verified", flush=True)


if __name__ == "__main__":
    main()
