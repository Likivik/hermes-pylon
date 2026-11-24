#!/usr/bin/env python3
"""Regenerate tools/route_scopes.json from a gateway source tree.

Usage:
    python3 scripts/generate_route_scopes.py /path/to/hermes_cli

Pass the `hermes_cli` package directory (the one containing `web_routers/`). Plugin-owned routes
are not in `web_routers`; they are classified by hand after a probe (an unknown profile returning
404 means the route is scoped) and kept in the file across regeneration.
"""
from __future__ import annotations

import glob
import hashlib
import json
import os
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "tools/route_scopes.json"


def handler_span(source: str, start: int) -> str:
    """The decorated handler's own text: from the decorator to the next top-level definition.

    A fixed character window is wrong in both directions — it borrows a neighbour's call and
    cuts off a long handler. Top-level indentation gives the real boundary.
    """
    rest = source[start:]
    lines = rest.splitlines(keepends=True)
    out: list[str] = []
    seen_def = False
    for line in lines:
        stripped = line.rstrip("\n")
        top_level = bool(stripped) and not stripped[0].isspace()
        starts_new = top_level and (
            stripped.startswith("@")
            or stripped.startswith("def ")
            or stripped.startswith("async def ")
            or stripped.startswith("class ")
        )
        # The decorated `def` itself is part of this handler; the next one ends it.
        if seen_def and starts_new:
            break
        out.append(line)
        if stripped.startswith("async def ") or stripped.startswith("def "):
            seen_def = True
        if seen_def and stripped == "" and len(out) > 3:
            # a blank line at column 0 after the signature ends a short handler
            pass
    return "".join(out)


def classify(source: str, start: int) -> str:
    span = handler_span(source, start)
    signature = re.search(r"(?:async )?def \w+\((.*?)\)\s*(?:->\s*[^:]+)?:", span, re.S)
    params = signature.group(1) if signature else ""
    if "profile" in params:
        return "query"
    # A body-scoped route either asks `destructive_profile` for the profile or reads it off the
    # request model (`body.profile`, `payload["profile"]`) — PATCH /api/sessions/{id} and
    # /api/sessions/import do the latter.
    if "destructive_profile(" in span or re.search(r"\.profile\b|\.get\(\s*[\"']profile[\"']", span):
        return "body"
    return "none"


def route_dirs(hermes_cli: Path) -> tuple[Path, ...]:
    """Directories that own gateway routes."""
    return (hermes_cli / "web_routers", hermes_cli / "dashboard_auth")


def collect(hermes_cli: Path) -> list[dict]:
    # Two directories own gateway routes: `web_routers/` and `dashboard_auth/` (the websocket
    # ticket lives in the latter). Scanning only the first left real routes invisible to the guard.
    # `.websocket(...)` is included because a websocket handshake is a route the client dials.
    routes: list[dict] = []
    for directory in route_dirs(hermes_cli):
      for path in sorted(glob.glob(str(directory / "*.py"))):
        source = Path(path).read_text()
        for match in re.finditer(r'@(\w+)\.(get|post|put|patch|delete|websocket)\("([^"]+)"[^)]*\)', source):
            routes.append(
                {
                    "method": "WEBSOCKET" if match.group(2) == "websocket" else match.group(2).upper(),
                    "path": match.group(3),
                    "scope": classify(source, match.end()),
                    "file": os.path.basename(path),
                }
            )
    return routes


def main(argv: list[str]) -> int:
    if len(argv) != 2:
        print(__doc__.strip(), file=sys.stderr)
        return 2
    hermes_cli = Path(argv[1])
    if not (hermes_cli / "web_routers").is_dir():
        print(f"no web_routers under {hermes_cli}", file=sys.stderr)
        return 2
    existing = json.loads(OUT.read_text())["routes"] if OUT.exists() else []
    plugin_routes = [r for r in existing if r.get("file") == "plugins"]
    routes = collect(hermes_cli)
    merged = {(r["path"], r["method"]): r for r in plugin_routes}
    for route in routes:
        merged[(route["path"], route["method"])] = route
    ordered = sorted(merged.values(), key=lambda r: (r["path"], r["method"]))
    # Provenance: which gateway source produced this inventory, so drift is detectable instead
    # of silent. A regenerated file that changes nothing still records what it was built from.
    generated_from = {
        os.path.relpath(str(f), str(hermes_cli)): hashlib.sha256(f.read_bytes()).hexdigest()[:16]
        for d in route_dirs(hermes_cli)
        for f in sorted(d.glob("*.py"))
    }
    OUT.write_text(json.dumps(
        {"gateway": argv[1], "generated_from": generated_from, "routes": ordered},
        indent=1) + "\n")
    print(f"wrote {len(ordered)} routes to {OUT}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
