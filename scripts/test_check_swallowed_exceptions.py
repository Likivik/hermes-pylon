#!/usr/bin/env python3
"""Tests for check_swallowed_exceptions.py.

The ratchet is the mechanism: it must fail on a new silent swallow, pass at the budget,
and refuse a stale budget in either direction.
"""

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

import check_swallowed_exceptions as guard

PLATFORM = "ui/src/wasmJsMain/kotlin/com/m57/hermescontrol/Swallow.kt"


def build(files: dict[str, str]) -> Path:
    root = Path(tempfile.mkdtemp())
    for rel, text in files.items():
        path = root / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")
    return root


class SwallowRatchetTest(unittest.TestCase):
    def test_logged_site_is_classified(self) -> None:
        root = build({PLATFORM: "fun f() { runCatching { work() }.onFailure { HermesLog.w(\"t\", \"x\") } }\n"})
        self.assertEqual([], guard.unclassified_sites(root))

    def test_marked_site_is_classified(self) -> None:
        root = build({PLATFORM: "fun f() {\n    // inventory: intentional-ignore\n    runCatching { work() }\n}\n"})
        self.assertEqual([], guard.unclassified_sites(root))

    def test_bare_site_is_unclassified(self) -> None:
        root = build({PLATFORM: "fun f() { runCatching { work() } }\n"})
        self.assertEqual(1, len(guard.unclassified_sites(root)))

    def test_budget_exact_passes(self) -> None:
        root = build({PLATFORM: "fun f() { runCatching { work() } }\n"})
        problems, _ = guard.check(root, 1)
        self.assertEqual([], problems)

    def test_new_swallow_fails(self) -> None:
        root = build({PLATFORM: "fun f() { runCatching { a() }; runCatching { b() } }\n"})
        problems, notes = guard.check(root, 1)
        self.assertTrue(problems, "a new silent swallow must fail the gate")
        self.assertTrue(notes)

    def test_stale_budget_fails(self) -> None:
        root = build({PLATFORM: "fun f() { runCatching { work() }.onFailure { HermesLog.w(\"t\", \"x\") } }\n"})
        problems, _ = guard.check(root, 1)
        self.assertTrue(problems, "a budget above the real count must fail so it cannot rot")

    def test_test_sources_are_ignored(self) -> None:
        root = build({"ui/src/commonTest/kotlin/X.kt": "fun f() { runCatching { work() } }\n"})
        self.assertEqual([], guard.unclassified_sites(root))


if __name__ == "__main__":
    unittest.main()
