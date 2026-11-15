#!/usr/bin/env python3
"""Assert the seed actually landed, before any browser test runs.

A half-seeded instance must fail here, loudly, rather than let a test assert nothing
and pass. Counts come from the same endpoints the app calls.

    PYLON_TEST_BASE=http://127.0.0.1:19119 PYLON_TEST_TOKEN=... python3 verify.py
"""

from __future__ import annotations

import os
import sys

from seed import FIXTURE_FILES, FIXTURES_DIR, call

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
    return sum(1 for entry in entries if not entry.get("isDirectory") and entry.get("name") in FIXTURE_FILES)


def count_env() -> int:
    _, payload = call("GET", "api/env")
    return len(payload) if isinstance(payload, dict) else -1


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
