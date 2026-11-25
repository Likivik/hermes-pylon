#!/usr/bin/env python3
"""Tests for check_actuals.py — the guard is only worth having if it fails.

Each case builds a miniature repo in a temp dir; the real tree is verified by running
the checker itself in CI.
"""

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

import check_actuals as guard

PLATFORM = "ui/src/wasmJsMain/kotlin/com/m57/hermescontrol/Capability.kt"


def build(files: dict[str, str]) -> Path:
    root = Path(tempfile.mkdtemp())
    for rel, text in files.items():
        path = root / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")
    return root


class ActualContractGuardTest(unittest.TestCase):
    def setUp(self) -> None:
        self._allow = dict(guard.ALLOW)
        guard.ALLOW.clear()

    def tearDown(self) -> None:
        guard.ALLOW.clear()
        guard.ALLOW.update(self._allow)

    def test_ordinary_actual_passes(self) -> None:
        root = build({PLATFORM: "actual fun compute(): Int = 42\n"})
        self.assertEqual(([], []), guard.check(root))

    def test_empty_body_fails(self) -> None:
        root = build({PLATFORM: "actual fun notifyAll() {}\n"})
        problems, _ = guard.check(root)
        self.assertTrue(problems, "an empty actual body must be reported")

    def test_empty_lambda_body_fails(self) -> None:
        root = build({PLATFORM: "actual fun rememberHook(): () -> Unit = {}\n"})
        problems, _ = guard.check(root)
        self.assertTrue(problems)

    def test_capability_constant_fails(self) -> None:
        root = build({PLATFORM: "actual fun isSupported(): Boolean = false\n"})
        problems, _ = guard.check(root)
        self.assertTrue(problems, "a capability answering a constant must be reported")

    def test_todo_fails(self) -> None:
        root = build({PLATFORM: "actual fun later(): Int = TODO()\n"})
        problems, _ = guard.check(root)
        self.assertTrue(problems)

    def test_unsupported_stub_fails(self) -> None:
        root = build({PLATFORM: 'actual fun share(): Unit = error("not supported on web")\n'})
        problems, _ = guard.check(root)
        self.assertTrue(problems)

    def test_allow_list_accepts_a_flagged_actual(self) -> None:
        root = build({PLATFORM: "actual fun isSupported(): Boolean = false\n"})
        guard.ALLOW[f"{PLATFORM}:isSupported"] = "web has no such capability; false is the contract"
        self.assertEqual(([], []), guard.check(root))

    def test_stale_allow_list_entry_fails(self) -> None:
        root = build({PLATFORM: "actual fun isSupported(): Boolean = compute()\n"})
        guard.ALLOW[f"{PLATFORM}:isSupported"] = "no longer true"
        problems, stale = guard.check(root)
        self.assertFalse(problems)
        self.assertTrue(stale, "an allow-list entry that no longer matches must fail")

    def test_commented_out_actual_does_not_count(self) -> None:
        root = build({PLATFORM: "// actual fun notifyAll() {}\n"})
        self.assertEqual(([], []), guard.check(root))

    def test_string_mention_of_a_stub_is_not_a_stub(self) -> None:
        root = build({PLATFORM: 'actual fun name(): String = "not supported on web"\n'})
        self.assertEqual(([], []), guard.check(root))


if __name__ == "__main__":
    unittest.main()
