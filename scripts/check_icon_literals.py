#!/usr/bin/env python3
"""Fail when a chat/shared component names an icon the subset font does not carry.

The subset font (`material_symbols_subset.ttf`, ~200 KB) covers exactly the names in the
`HermesIcon` enum. Any other name falls through to the full variable font — **10.7 MB** — which
the browser fetches on demand. `EmptyState` defaulted to `icon = "inbox"`, `inbox` was not in the
subset, so the very first empty screen of a fresh chat pulled the whole font for one glyph: a
multi-second tofu window on a phone over a tailnet, and 11 MB of payload.

So the rule is mechanical: every icon name written as a string literal in UI code must appear in
the subset codepoints file. Names reached through `HermesIcon` are covered by construction.

    python3 scripts/check_icon_literals.py            # exit 0/1
    python3 scripts/check_icon_literals.py --self-test # prove the check can fail
"""

from __future__ import annotations

import argparse
import re
import sys
import tempfile
from pathlib import Path

SUBSET = Path("ui/src/commonMain/composeResources/files/material_symbols_subset.codepoints")
UI_ROOT = Path("ui/src")
# `icon = "name"`, `MaterialSymbol(name = "name")`, `Symbol(name = "name")`.
LITERAL = re.compile(r'(?:\bicon\s*=\s*|(?:MaterialSymbol|Symbol)\s*\(\s*name\s*=\s*)"([a-z_0-9]+)"')


def subset_names(path: Path) -> set[str]:
    if not path.is_file():
        raise SystemExit(f"missing subset codepoints: {path}")
    names = set()
    for line in path.read_text(encoding="utf-8").splitlines():
        parts = line.split()
        if parts:
            names.add(parts[0])
    return names


def offenders(root: Path, names: set[str]) -> list[str]:
    found: list[str] = []
    for path in sorted(root.rglob("*.kt")):
        for number, line in enumerate(path.read_text(encoding="utf-8", errors="replace").splitlines(), 1):
            for name in LITERAL.findall(line):
                if name not in names:
                    found.append(f"{path}:{number}: icon '{name}' is not in the subset font")
    return found


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args(argv)

    if args.self_test:
        with tempfile.TemporaryDirectory() as tmp:
            fake_root = Path(tmp)
            (fake_root / "Bogus.kt").write_text('EmptyState(icon = "definitely_not_a_symbol")\n', encoding="utf-8")
            names = subset_names(SUBSET)
            bad = offenders(fake_root, names)
            if not bad:
                print("SELF-TEST FAILED: the check did not flag an unknown icon", file=sys.stderr)
                return 1
            print(f"self-test ok: {bad[0].split(': ')[-1]}")
        return 0

    names = subset_names(SUBSET)
    bad = offenders(UI_ROOT, names)
    if bad:
        for line in bad:
            print(f"FAIL {line}", file=sys.stderr)
        print(
            "an icon outside the subset pulls the 10.7 MB full font; add it to HermesIcon and run "
            "scripts/make-icon-subset.sh",
            file=sys.stderr,
        )
        return 1
    print(f"ok: every icon literal in {UI_ROOT} is in the {len(names)}-name subset")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
