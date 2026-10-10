#!/usr/bin/env python3
"""Guard the multiplatform boundary of `:shared/commonMain`.

`commonMain` is the code every target shares, so it must not touch platform
APIs: an `androidx`/`java`/`okhttp3`/`retrofit2` import there compiles for
Android and the JVM and breaks the moment a web or desktop target is added.

This check exists because that boundary rots silently. Platform code belongs in
`androidMain`/`jvmMain` (and any future `jsMain`/`wasmJsMain`), behind the
`expect`/`actual` declarations in `commonMain`.

Also verifies that every `expect` declaration has an `actual` in each declared
target source set, so a half-migrated target is caught rather than compiled.

Usage:  python3 scripts/check_kmp_boundary.py [--shared-dir PATH]
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FORBIDDEN_ROOTS = {"android", "androidx", "java", "javax", "okhttp3", "retrofit2"}

IMPORT_RE = re.compile(r"^\s*import\s+([\w.]+)", re.MULTILINE)
EXPECT_RE = re.compile(r"^\s*(?:internal\s+|public\s+|protected\s+)?expect\s+(?:fun|val|var|class|object|interface)\s+([A-Za-z_]\w*)", re.MULTILINE)

# Target source sets whose directories are siblings of commonMain.
TARGET_SOURCE_SETS = ("androidMain", "jvmMain", "jsMain", "wasmJsMain", "iosMain", "desktopMain")


def strip_comments(text: str) -> str:
    """Drop block and line comments.

    Mentions of platform types are wanted in KDoc (they record what a given
    declaration replaced); only real code counts.
    """
    without_blocks = re.sub(r"/\*.*?\*/", "", text, flags=re.DOTALL)
    return re.sub(r"//[^\n]*", "", without_blocks)


def scan(base_dir: Path) -> tuple[list[str], list[str]]:
    """Return (violations, missing_actuals) for a `shared/src`-like directory."""
    common = base_dir / "commonMain"
    violations: list[str] = []
    for path in sorted(common.rglob("*.kt")):
        for match in IMPORT_RE.finditer(strip_comments(path.read_text(encoding="utf-8"))):
            imported = match.group(1)
            if imported.split(".")[0] in FORBIDDEN_ROOTS:
                violations.append(f"{path.relative_to(base_dir.parent.parent)}: imports {imported}")

    expect_names: list[str] = []
    for path in sorted(common.rglob("*.kt")):
        expect_names += EXPECT_RE.findall(strip_comments(path.read_text(encoding="utf-8")))

    missing: list[str] = []
    for source_set in TARGET_SOURCE_SETS:
        directory = base_dir / source_set
        if not directory.is_dir():
            continue
        blob = "\n".join(strip_comments(p.read_text(encoding="utf-8")) for p in directory.rglob("*.kt"))
        for name in expect_names:
            if not re.search(rf"\bactual\b[^\n]*\b{re.escape(name)}\b", blob):
                missing.append(f"{source_set}: no `actual` for expect `{name}`")
    return violations, missing


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--shared-dir",
        type=Path,
        default=ROOT / "shared" / "src",
        help="the :shared/src directory to check (default: the repository's)",
    )
    args = parser.parse_args()

    violations, missing = scan(args.shared_dir)
    for violation in violations:
        print(f"boundary violation: {violation}")
    for gap in missing:
        print(f"unimplemented expect: {gap}")

    if violations or missing:
        print(
            f"\nFAILED: {len(violations)} platform API(s) in commonMain, "
            f"{len(missing)} expect(s) without an actual.\n"
            "Move platform code into the target source set and expose it through "
            "an expect/actual declaration.",
            file=sys.stderr,
        )
        return 1

    print(f"OK: {args.shared_dir}/commonMain is free of platform APIs; every expect has its actuals.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
