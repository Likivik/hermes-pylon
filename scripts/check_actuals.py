#!/usr/bin/env python3
"""`actual` implementations must not quietly do nothing.

The compiler checks that an `actual` exists and has the right shape. It cannot check
that the implementation means anything: an empty body, a `TODO()`, or a capability
function that always returns `null`/`false` all compile green and silently disable a
feature on one platform. That is the second class in the silent-failure plan.

Policy, not a blanket ban:
  * every flag must be dismissed in ALLOW with a reason;
  * an ALLOW entry whose code no longer trips a rule is itself an error, so the
    allow-list cannot rot into a list of lies.

Usage: python3 scripts/check_actuals.py [--repo-root PATH]
Exit 0 clean, 1 violations.
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

# Flags that are acceptable, each with the reason it is acceptable.
# Key: "<path relative to repo root>:<symbol>"
ALLOW: dict[str, str] = {
    "ui/src/wasmJsMain/kotlin/com/m57/hermescontrol/util/TestEnvironmentWeb.kt:isRunningUnderTest": (
        "the browser harness cannot detect itself yet; the value only shortens timeouts, "
        "so a false negative costs seconds, not correctness"
    ),
    "ui/src/desktopWebMain/kotlin/com/m57/hermescontrol/ui/common/AppLanguageRestarter.desktopWeb.kt:rememberAppLanguageRestarter": (
        "no desktop shell ships; the restart hook is Android-only until one exists"
    ),
    "ui/src/desktopWebMain/kotlin/com/m57/hermescontrol/theme/DynamicColorSchemeActual.desktopWeb.kt:dynamicColorSchemeOrNull": (
        "dynamic (wallpaper) colour does not exist on the web; null is the documented contract"
    ),
    "ui/src/jvmMain/kotlin/com/m57/hermescontrol/BuildFlags.kt:isDebug": (
        "build metadata, constant by definition"
    ),
    "ui/src/wasmJsMain/kotlin/com/m57/hermescontrol/BuildFlags.kt:isDebug": (
        "build metadata, constant by definition"
    ),
    "ui/src/jvmTest/kotlin/com/m57/hermescontrol/ui/PlatformUnderTest.jvm.kt:isWebTestTarget": (
        "this constant IS the platform answer the tests consult; a non-constant would be circular"
    ),
    "ui/src/wasmJsTest/kotlin/com/m57/hermescontrol/ui/PlatformUnderTest.wasmJs.kt:isWebTestTarget": (
        "this constant IS the platform answer the tests consult; a non-constant would be circular"
    ),
    "shared/src/wasmJsMain/kotlin/com/m57/hermescontrol/data/remote/NetworkRetry.wasmJs.kt:isRetryable": (
        "contract, verified 2026-11-17: fetch reports one opaque transport failure, so the "
        "JVM taxonomy has nothing to match. This is consulted only from the IOException "
        "branch (NetworkResult.kt:134); HTTP 4xx/429/5xx are classified separately at "
        "NetworkResult.kt:117, so status errors never reach it. An earlier note here called "
        "this a defect — that was wrong and is retracted."
    ),
}

LINE_COMMENT_RE = re.compile(r"//[^\n]*")
BLOCK_COMMENT_RE = re.compile(r"/\*.*?\*/", re.DOTALL)
STRING_LITERAL_RE = re.compile(r'"(?:[^"\\]|\\.)*"')

DECL_RE = re.compile(r"\bactual\s+(?:fun|val|var)\s+([A-Za-z0-9_]+)")
CAPABILITY_NAME_RE = re.compile(r"^(?:is[A-Z_]|can[A-Z_]|.*OrNull$)")
CONSTANT_RETURN_RE = re.compile(r"=\s*(null|false|true|0|\"\")\s*(?:$|\n|\})")
EMPTY_BODY_RE = re.compile(r"=\s*\{\s*\}|\{\s*\}")
TODO_RE = re.compile(r"\bTODO\(\)")
UNSUPPORTED_RE = re.compile(r"error\(\s*\"[^\"]*(?:not supported|unsupported|stub|no-?op)", re.I)


def strip_noise(text: str) -> str:
    """Remove comments so a doc mention is not a declaration.

    String literals are kept: `error("not supported")` is exactly what the
    UNSUPPORTED rule must see, while a *bare* string mentioning the phrase is not a
    stub (the rule requires the `error(` call).
    """
    without_blocks = BLOCK_COMMENT_RE.sub("", text)
    return LINE_COMMENT_RE.sub("", without_blocks)


def source_files(root: Path) -> list[Path]:
    files: list[Path] = []
    for pattern in ("*/src/*Main/**/*.kt", "*/src/*Test/**/*.kt"):
        for path in root.glob(pattern):
            if "build/" in path.parts:
                continue
            files.append(path)
    return sorted(set(files))


def classify(symbol: str, decl: str) -> str | None:
    """Return the rule name this declaration trips, or None."""
    if TODO_RE.search(decl):
        return "TODO"
    if UNSUPPORTED_RE.search(decl):
        return "UNSUPPORTED"
    if CAPABILITY_NAME_RE.match(symbol):
        if EMPTY_BODY_RE.search(decl) or CONSTANT_RETURN_RE.search(decl):
            return "CAPABILITY-CONSTANT"
        return None
    if EMPTY_BODY_RE.search(decl):
        return "EMPTY-BODY"
    return None


def find_violations(root: Path) -> tuple[list[tuple[str, str, str]], set[str]]:
    violations: list[tuple[str, str, str]] = []
    seen: set[str] = set()
    for path in source_files(root):
        rel = path.relative_to(root).as_posix()
        code = strip_noise(path.read_text(encoding="utf-8", errors="ignore"))
        for match in DECL_RE.finditer(code):
            symbol = match.group(1)
            decl = code[match.start() : match.start() + 240].split("\n\n")[0]
            rule = classify(symbol, decl)
            if rule is None:
                continue
            key = f"{rel}:{symbol}"
            seen.add(key)
            if key not in ALLOW:
                violations.append((key, rule, decl.strip()[:110].replace("\n", " ")))
    return violations, seen


def check(root: Path) -> tuple[list[str], list[str]]:
    violations, seen = find_violations(root)
    problems = [
        f"{key}: {rule} — allow-list it in scripts/check_actuals.py with a reason, "
        f"or implement it ({snippet})"
        for key, rule, snippet in violations
    ]
    stale = [
        f"{key}: stale allow-list entry — the code no longer trips any rule"
        for key in sorted(set(ALLOW) - seen)
    ]
    return problems, stale


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo-root", default=Path(__file__).resolve().parent.parent)
    args = parser.parse_args()
    root = Path(args.repo_root).resolve()

    problems, stale = check(root)
    for problem in problems:
        print(f"FAIL {problem}")
    for entry in stale:
        print(f"STALE {entry}")
    if problems or stale:
        return 1

    print(f"OK every silent actual is either implemented or allow-listed ({len(ALLOW)} accepted)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
