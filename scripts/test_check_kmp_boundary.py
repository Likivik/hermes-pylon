#!/usr/bin/env python3
"""Tests for scripts/check_kmp_boundary.py."""

from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from check_kmp_boundary import scan, strip_comments  # noqa: E402


def write(base: Path, relative: str, text: str) -> None:
    target = base / relative
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(text, encoding="utf-8")


class StripCommentsTest(unittest.TestCase):
    def test_line_and_block_comments_are_dropped(self) -> None:
        text = (
            "// import androidx.foo records what this replaced\n"
            "/* import okhttp3.Request */\n"
            "import kotlinx.coroutines.launch"
        )
        self.assertNotIn("androidx", strip_comments(text))
        self.assertNotIn("okhttp3", strip_comments(text))
        self.assertIn("kotlinx.coroutines.launch", strip_comments(text))


class ScanTest(unittest.TestCase):
    def test_clean_common_main_passes(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp) / "src"
            write(base, "commonMain/A.kt", "import kotlinx.coroutines.launch\n")
            write(base, "jvmMain/A.jvm.kt", "actual fun nowMillis(): Long = 0\n")
            violations, missing = scan(base)
            self.assertEqual([], violations)
            self.assertEqual([], missing)

    def test_platform_import_in_common_main_is_reported(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp) / "src"
            write(base, "commonMain/A.kt", "import okhttp3.Request\n")
            violations, _ = scan(base)
            self.assertEqual(1, len(violations))
            self.assertIn("okhttp3.Request", violations[0])

    def test_expect_without_actual_is_reported(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp) / "src"
            write(base, "commonMain/Clock.kt", "expect fun nowMillis(): Long\n")
            write(base, "jvmMain/Clock.jvm.kt", "actual fun nowMillis(): Long = 0\n")
            write(base, "androidMain/Other.kt", "class Unrelated\n")
            _, missing = scan(base)
            self.assertEqual(1, len(missing))
            self.assertIn("androidMain", missing[0])

    def test_untargeted_source_set_is_ignored(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp) / "src"
            write(base, "commonMain/Clock.kt", "expect fun nowMillis(): Long\n")
            _, missing = scan(base)
            self.assertEqual([], missing)


if __name__ == "__main__":
    unittest.main()
