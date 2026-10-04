#!/usr/bin/env python3
"""Read-only, fail-closed upstream and public security advisory change monitor.

Output is bounded JSON containing only commit IDs and advisory IDs.
No API bodies, URLs, titles, messages, or diagnostics are printed.
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import uuid

SHA = re.compile(r"[0-9a-f]{40}\Z")
ADVISORY = re.compile(r"(?:GHSA-[\w-]+|CVE-\d{4}-\d{4,})\Z")
REPO = re.compile(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+\Z")
LIMIT = 100


class MonitorError(Exception):
    pass


class MaintenanceLock:
    """Exclusive operator lock; a stale lock requires manual adjudication."""

    def __init__(self, path: Path):
        self.path = path
        self.owner = str(uuid.uuid4())
        self.inode: int | None = None

    def acquire(self) -> None:
        try:
            self.path.mkdir()
        except FileExistsError as exc:
            raise MonitorError("maintenance lock held; inspect owner") from exc
        self.inode = self.path.stat().st_ino
        try:
            with (self.path / "owner.json").open(
                "x", encoding="utf-8"
            ) as stream:
                json.dump({"uuid": self.owner, "pid": os.getpid()}, stream)
                stream.flush()
                os.fsync(stream.fileno())
        except BaseException:
            # Retain incomplete ownership for manual investigation.
            raise

    def release(self) -> None:
        if self.inode is None or self.path.stat().st_ino != self.inode:
            raise MonitorError("maintenance lock replaced; do not remove")
        with (self.path / "owner.json").open(encoding="utf-8") as stream:
            owner = json.load(stream)
        if owner != {"uuid": self.owner, "pid": os.getpid()}:
            raise MonitorError("maintenance lock owner changed; do not remove")
        (self.path / "owner.json").unlink()
        self.path.rmdir()
        self.inode = None


def command(*argv: str) -> str:
    try:
        result = subprocess.run(
            argv, capture_output=True, text=True, timeout=30, check=False
        )
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise MonitorError("command unavailable or timed out") from exc
    if result.returncode:
        raise MonitorError(
            "read-only lookup failed (authentication, permission, network, "
            "or API error)"
        )
    return result.stdout


def api(path: str) -> object:
    try:
        return json.loads(command("gh", "api", "--method", "GET", path))
    except ValueError as exc:
        raise MonitorError("invalid API response") from exc


def require_sha(value: object) -> str:
    if not isinstance(value, str) or not SHA.fullmatch(value):
        raise MonitorError("invalid commit ID in response")
    return value


def snapshot(repo: str, upstream: str) -> dict[str, object]:
    if not REPO.fullmatch(repo) or not REPO.fullmatch(upstream):
        raise MonitorError("invalid repository identifier")
    heads = []
    for name in (repo, upstream):
        data = api(f"repos/{name}/git/ref/heads/main")
        if not isinstance(data, dict) or not isinstance(
            data.get("object"), dict
        ):
            raise MonitorError("invalid ref response")
        heads.append(require_sha(data["object"].get("sha")))
    # Public repository advisories only. Private Dependabot alerts require a
    # separate permissioned monitor; do not interpret an empty list as coverage.
    advisories = api(f"repos/{repo}/security-advisories?per_page={LIMIT}")
    if not isinstance(advisories, list) or len(advisories) >= LIMIT:
        raise MonitorError(
            "security advisory response malformed or pagination limit reached"
        )
    ids = []
    for item in advisories:
        if not isinstance(item, dict) or not isinstance(
            item.get("ghsa_id"), str
        ):
            raise MonitorError("invalid advisory response")
        advisory_id = item["ghsa_id"]
        if not ADVISORY.fullmatch(advisory_id):
            raise MonitorError("invalid advisory ID")
        ids.append(advisory_id)
    return {
        "fork_head": heads[0],
        "upstream_head": heads[1],
        "public_advisory_ids": sorted(set(ids)),
    }


def compare(
    before: dict[str, object], after: dict[str, object]
) -> dict[str, object]:
    def validate(state: dict[str, object]) -> list[str]:
        for key in ("fork_head", "upstream_head"):
            require_sha(state.get(key))
        ids = state.get("public_advisory_ids")
        if not isinstance(ids, list) or any(
            not isinstance(x, str) or not ADVISORY.fullmatch(x) for x in ids
        ):
            raise MonitorError("invalid snapshot advisories")
        return ids

    previous = validate(before)
    current_ids = validate(after)
    return {
        "fork_changed": before["fork_head"] != after["fork_head"],
        "upstream_changed": before["upstream_head"] != after["upstream_head"],
        "new_public_advisory_ids": sorted(set(current_ids) - set(previous)),
        "removed_public_advisory_ids": sorted(set(previous) - set(current_ids)),
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", default="saralilyb/hermes-mobile")
    parser.add_argument("--upstream", default="Hy4ri/hermes-mobile")
    parser.add_argument(
        "--baseline", help="previous JSON snapshot; absent means snapshot only"
    )
    args = parser.parse_args()
    try:
        current = snapshot(args.repo, args.upstream)
        result = {"snapshot": current}
        if args.baseline:
            with open(args.baseline, encoding="utf-8") as stream:
                previous = json.load(stream)
            if not isinstance(previous, dict):
                raise MonitorError("invalid baseline")
            result["changes"] = compare(previous, current)
        print(json.dumps(result, sort_keys=True))
        return 0
    except (MonitorError, OSError, ValueError) as exc:
        # External error details may contain credentials or URLs.
        label = (
            str(exc) if isinstance(exc, MonitorError)
            else "baseline unreadable or invalid"
        )
        print(f"maintenance monitor: {label}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
