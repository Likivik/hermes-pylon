#!/usr/bin/env python3
"""Fail when the app calls a gateway route that is not in the measured route inventory.

The inventory (`tools/route_scopes.json`) is generated from the gateway's own routers by
`scripts/generate_route_scopes.py`, which records, per route, whether it accepts a `profile`
query parameter, takes one in a body model, or does not take one at all.

This guard has one job: **no endpoint ships without someone having classified it.** If the client
calls a path that the inventory does not know, either the app is newer than the inventory (regenerate
and classify) or the route was renamed upstream (fix the client). Both are failures worth stopping.

Exit codes: 0 clean, 1 drift found, 2 inventory missing.
"""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
API = ROOT / "shared/src/commonMain/kotlin/com/m57/hermescontrol/data/remote/HermesApi.kt"
INVENTORY = ROOT / "tools/route_scopes.json"

# Path helpers that build the prefix of a request path. Keep this list short and reviewed:
# a helper that is not listed surfaces as an unmatched route, which is the point.
HELPERS = {
    "sessionPath": "api/sessions/{}",
}

# `api/` route literals are not confined to the one interface any more. They live across the
# shared layer, the websocket client and the UI view models. Reading a single file left the rest
# unguarded, which is how callers escaped this check when the interface was split up.
CLIENT_ROOTS = (ROOT / "shared/src", ROOT / "ui/src")


def client_sources() -> list[Path]:
    """Every production file that may hold an `api/` route literal."""
    if not API.is_relative_to(ROOT):
        return [API]
    found = {API}
    for root in CLIENT_ROOTS:
        for path in root.rglob("*.kt"):
            if "Test" not in path.name:
                found.add(path)
    return sorted(found)


def normalise(path: str) -> str:
    """Compare paths by shape, not by parameter name."""
    path = path if path.startswith("/") else "/" + path
    path = re.sub(r"\$\{[^}]*\}|\$[A-Za-z_][A-Za-z0-9_]*", "{}", path)
    path = re.sub(r"\{[^}]*\}", "{}", path)
    path = re.sub(r"\{:\w+\}", "{}", path)
    return re.sub(r"/+", "/", path).rstrip("/") or "/"


def client_paths(source: str) -> set[str]:
    source = source.replace("\n", " ")
    for helper, shape in HELPERS.items():
        source = re.sub(rf"{helper}\(\s*[A-Za-z_][A-Za-z0-9_]*\s*\)", shape, source)
    found: set[str] = set()
    # plain literals: "api/..." or "/api/..."
    for literal in re.findall(r'"((?:/)?api/[^"]*)"', source):
        found.add(normalise(literal))
    # templates that start a path: "${...}/..." with a known shape already substituted
    for literal in re.findall(r'"(api/[^"]*)"', source):
        found.add(normalise(literal))
    return {p for p in found if "{}" in p or re.match(r"^/api/[a-z0-9/_-]+$", p)}


def inventory_paths(routes: list[dict]) -> set[str]:
    return {normalise(r["path"]) for r in routes}


def functions(source: str) -> list[tuple[str, str]]:
    """Split the client into (function name, body) blocks."""
    blocks = re.split(r"\n    (?:private )?suspend fun ", "\n" + source)
    out: list[tuple[str, str]] = []
    for block in blocks[1:]:
        name = re.match(r"([A-Za-z0-9_]+)", block)
        if name:
            out.append((name.group(1), block))
    return out


def model_carries_profile(type_name: str, sources: list[Path]) -> bool:
    """A request model carries the profile when its constructor declares one."""
    pattern = re.compile(rf"class {re.escape(type_name)}\b[^(]*\((.*?)\)", re.S)
    for path in sources:
        # Annotations carry their own parentheses; strip them before matching the constructor.
        text = re.sub(r"@\w+\([^)]*\)", "", path.read_text())
        for match in pattern.finditer(text):
            if re.search(r"\bprofile\b", match.group(1)):
                return True
    return False


def body_scopes(client: str, routes: list[dict], sources: list[Path]) -> set[str]:
    """A function calling a body-scoped route must send a model that carries the profile.

    Only the *direct* call path counts: matching every literal in a block drags in paths from
    neighbouring helpers and reports functions that never touch the route.
    """
    body_paths = {normalise(r["path"]) for r in routes if r["scope"] == "body"}
    missing: set[str] = set()
    for name, block in functions(client):
        joined = block.replace("\n", " ")
        called = {
            normalise(p)
            for p in re.findall(r'\b(?:patch|post|put|delete)\(\s*(?:url\(\s*)?"((?:/)?api/[^"]*)"', joined)
        }
        for helper, shape in HELPERS.items():
            if re.search(rf"\b(?:patch|post|put|delete)\(\s*(?:url\(\s*)?{helper}\(", joined):
                called.add(normalise(shape))
        if not (called & body_paths):
            continue
        signature = re.match(r"[^(]*\((.*?)\)\s*:", joined, re.S)
        params = re.findall(r"\w+\s*:\s*([A-Z]\w+)", signature.group(1) if signature else "")
        if "profile" in block or any(model_carries_profile(t, sources) for t in params):
            continue
        missing.add(name)
    return missing


def report(unknown: set[str], known: set[str], unscoped: set[str] | None = None) -> int:
    if not unknown and not unscoped:
        print(f"route scope: {len(known)} client paths all classified")
        return 0
    if unscoped:
        print("route scope: body-scoped routes called without a profile in the body:", file=sys.stderr)
        for name in sorted(unscoped):
            print(f"  {name}", file=sys.stderr)
    print("route scope: the client calls routes the measured inventory does not know:", file=sys.stderr)
    for path in sorted(unknown):
        print(f"  {path}", file=sys.stderr)
    print(
        "Regenerate tools/route_scopes.json from the gateway and classify the new routes, "
        "or fix the client path.",
        file=sys.stderr,
    )
    return 1


def main() -> int:
    if not INVENTORY.exists():
        print(f"route scope: missing inventory at {INVENTORY}", file=sys.stderr)
        return 2
    routes = json.loads(INVENTORY.read_text())["routes"]
    known = inventory_paths(routes)
    sources = sorted((ROOT / "shared/src/commonMain").rglob("*.kt"))
    called: set[str] = set()
    unscoped: set[str] = set()
    for path in client_sources():
        text = path.read_text()
        called |= client_paths(text)
        unscoped |= body_scopes(text, routes, sources)
    return report(called - known, called, unscoped)


if __name__ == "__main__":
    raise SystemExit(main())
