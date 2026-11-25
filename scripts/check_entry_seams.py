#!/usr/bin/env python3
"""Every platform entry that wires the WS client must install the same core seams.

The web entry shipped with `installAuth` and `installTransport` but no
`installHttpClient`. Nothing failed loudly: gated mode could not mint a ticket, so
`openSocket` deferred forever and the UI sat on "Reconnecting..." while every REST
call returned 200. The runtime now reports that state (`MISSING_HTTP_CLIENT`), and
this check stops the next entry from being written the same way.

Rules:
  * every entry installs REQUIRED_EVERYWHERE;
  * an install name outside REQUIRED_EVERYWHERE / PLATFORM_OPTIONAL is an error, so
    adding a seam forces the author to say which category it belongs to.

Usage: python3 scripts/check_entry_seams.py [--repo-root PATH]
Exit 0 clean, 1 violations.
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

# Seams the socket cannot work without, on every platform.
REQUIRED_EVERYWHERE = {
    "installAuth",
    "installTransport",
    "installHttpClient",
}

# Legitimately platform-specific: Android has a ConnectivityManager and a loopback
# token refresher; the browser has neither.
PLATFORM_OPTIONAL = {
    "installNetworkMonitor",
    "installLoopbackTokenRefresher",
}

INSTALL_RE = re.compile(r"HermesWsClient\.(install[A-Za-z0-9_]+)")
LINE_COMMENT_RE = re.compile(r"//[^\n]*")
BLOCK_COMMENT_RE = re.compile(r"/\*.*?\*/", re.DOTALL)
ENTRY_GLOBS = (
    "app/src/main/**/*.kt",
    "ui/src/*Main/**/*.kt",
    "shared/src/*Main/**/*.kt",
)


def find_entries(root: Path) -> dict[Path, set[str]]:
    entries: dict[Path, set[str]] = {}
    for pattern in ENTRY_GLOBS:
        for path in root.glob(pattern):
            if "build/" in path.parts:
                continue
            try:
                text = path.read_text(encoding="utf-8")
            except (OSError, UnicodeDecodeError):
                continue
            # Comments and docs name the seams too; only live calls count.
            code = BLOCK_COMMENT_RE.sub("", LINE_COMMENT_RE.sub("", text))
            installs = set(INSTALL_RE.findall(code))
            # The client's own file documents the seam names; it is not an entry.
            if installs and "object HermesWsClient" not in text:
                entries[path] = installs
    return entries


def check(entries: dict[Path, set[str]]) -> list[str]:
    problems: list[str] = []
    known = REQUIRED_EVERYWHERE | PLATFORM_OPTIONAL

    for path, installs in sorted(entries.items()):
        rel = path.as_posix()
        missing = REQUIRED_EVERYWHERE - installs
        if missing:
            problems.append(
                f"{rel}: missing {', '.join(sorted(missing))} — "
                "the socket cannot work on this platform without it"
            )
        unknown = installs - known
        if unknown:
            problems.append(
                f"{rel}: unknown seam(s) {', '.join(sorted(unknown))} — add each one to "
                "REQUIRED_EVERYWHERE (wire it in every entry) or PLATFORM_OPTIONAL (say why)"
            )
    if not entries:
        problems.append("no platform entry installs the WS seams — the check found nothing to verify")
    return problems


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo-root", default=Path(__file__).resolve().parent.parent)
    args = parser.parse_args()
    root = Path(args.repo_root).resolve()

    entries = find_entries(root)
    for path, installs in sorted(entries.items()):
        print(f"{path.relative_to(root)}: {', '.join(sorted(installs))}")

    problems = check(entries)
    if problems:
        print()
        for problem in problems:
            print(f"FAIL {problem}")
        return 1

    print(f"\nOK {len(entries)} entries install every required seam")
    return 0


if __name__ == "__main__":
    sys.exit(main())
