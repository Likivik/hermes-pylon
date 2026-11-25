#!/usr/bin/env python3
"""Tests for check_entry_seams.py — the guard is only worth having if it fails.

Each case builds a miniature repo in a temp dir, because the real tree is the thing
under test elsewhere.
"""

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

import check_entry_seams as guard

WEB_ENTRY = "ui/src/wasmJsMain/kotlin/com/m57/hermescontrol/ui/Main.kt"
ANDROID_ENTRY = "app/src/main/java/com/m57/hermescontrol/HermesControlApp.kt"
CLIENT = "shared/src/commonMain/kotlin/com/m57/hermescontrol/data/ws/HermesWsClient.kt"

ALL_SEAMS = """
fun wire() {
    HermesWsClient.installAuth(auth)
    HermesWsClient.installTransport(transport)
    HermesWsClient.installHttpClient(client)
}
"""


def build(files: dict[str, str]) -> Path:
    root = Path(tempfile.mkdtemp())
    for rel, text in files.items():
        path = root / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(text, encoding="utf-8")
    return root


class EntrySeamGuardTest(unittest.TestCase):
    def test_clean_tree_passes(self) -> None:
        root = build({WEB_ENTRY: ALL_SEAMS, ANDROID_ENTRY: ALL_SEAMS})
        self.assertEqual([], guard.check(guard.find_entries(root)))

    def test_missing_http_client_fails(self) -> None:
        partial = """
fun wire() {
    HermesWsClient.installAuth(auth)
    HermesWsClient.installTransport(transport)
}
"""
        root = build({WEB_ENTRY: partial, ANDROID_ENTRY: ALL_SEAMS})
        problems = guard.check(guard.find_entries(root))
        self.assertTrue(problems, "a missing installHttpClient must be reported")
        self.assertIn("installHttpClient", problems[0])

    def test_commented_out_install_does_not_count(self) -> None:
        commented = """
fun wire() {
    HermesWsClient.installAuth(auth)
    HermesWsClient.installTransport(transport)
    // HermesWsClient.installHttpClient(client)
}
"""
        root = build({WEB_ENTRY: commented})
        problems = guard.check(guard.find_entries(root))
        self.assertTrue(problems, "a commented-out install is not an install")

    def test_unknown_seam_fails(self) -> None:
        extra = ALL_SEAMS + "\n    HermesWsClient.installSomethingNew(x)\n"
        root = build({WEB_ENTRY: extra})
        problems = guard.check(guard.find_entries(root))
        self.assertTrue(problems, "a new seam must be classified before it ships")
        self.assertIn("installSomethingNew", " ".join(problems))

    def test_platform_optional_seams_are_allowed(self) -> None:
        android = ALL_SEAMS + """
    HermesWsClient.installNetworkMonitor(monitor)
    HermesWsClient.installLoopbackTokenRefresher { }
"""
        root = build({WEB_ENTRY: ALL_SEAMS, ANDROID_ENTRY: android})
        self.assertEqual([], guard.check(guard.find_entries(root)))

    def test_client_definition_file_is_not_an_entry(self) -> None:
        definition = "object HermesWsClient {\n // mentions installHttpClient in docs\n}\n"
        root = build({CLIENT: definition, WEB_ENTRY: ALL_SEAMS})
        names = {p.relative_to(root).as_posix() for p in guard.find_entries(root)}
        self.assertNotIn(CLIENT, names)

    def test_no_entries_at_all_fails(self) -> None:
        root = build({"README.md": "nothing here"})
        problems = guard.check(guard.find_entries(root))
        self.assertTrue(problems, "finding no entry is a broken check, not a clean tree")


if __name__ == "__main__":
    unittest.main()
