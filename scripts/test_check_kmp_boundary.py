#!/usr/bin/env python3
"""Tests for scripts/check_kmp_boundary.py."""

from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from check_kmp_boundary import scan, scan_constructs, strip_comments  # noqa: E402


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

    def test_generic_and_extension_expects_are_seen(self) -> None:
        """A generic expect is invisible to a naive `expect fun NAME` pattern.

        Both forms below are real declarations in this repo; missing them means
        the check silently passes a half-migrated target.
        """
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp) / "src"
            write(
                base,
                "commonMain/Collections.kt",
                "expect fun <K, V> concurrentMutableMap(): MutableMap<K, V>\n"
                "expect fun <K, V> MutableMap<K, V>.removeIfValue(key: K, value: V): Boolean\n",
            )
            write(
                base,
                "jvmMain/Collections.jvm.kt",
                "actual fun <K, V> concurrentMutableMap(): MutableMap<K, V> = TODO()\n",
            )
            _, missing = scan(base)
            self.assertEqual(1, len(missing))
            self.assertIn("removeIfValue", missing[0])


class UiPolicyTest(unittest.TestCase):
    """`:ui` is Compose Multiplatform: KMP androidx is fine, Android-only is not."""

    def _scan_ui(self, imports: str) -> list[str]:
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp) / "ui" / "src"
            write(base, "commonMain/A.kt", imports)
            violations, _ = scan(base)
            return violations

    def test_compose_multiplatform_imports_are_allowed(self) -> None:
        violations = self._scan_ui(
            "import androidx.compose.runtime.getValue\n"
            "import androidx.compose.runtime.mutableStateOf\n"
            "import androidx.lifecycle.ViewModel\n",
        )
        self.assertEqual([], violations)

    def test_android_platform_import_is_rejected(self) -> None:
        violations = self._scan_ui("import android.content.Context\n")
        self.assertEqual(1, len(violations))
        self.assertIn("android.content.Context", violations[0])

    def test_non_kmp_androidx_artifact_is_rejected(self) -> None:
        violations = self._scan_ui("import androidx.room.Room\n")
        self.assertEqual(1, len(violations))
        self.assertIn("androidx.room.Room", violations[0])

    def test_shared_still_forbids_androidx(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp) / "shared" / "src"
            write(base, "commonMain/A.kt", "import androidx.compose.runtime.getValue\n")
            violations, _ = scan(base)
            self.assertEqual(1, len(violations))


class ConstructScanTest(unittest.TestCase):
    """The import scan cannot see a symbol; these constructs need their own check."""

    def scan_text(self, text: str) -> list[str]:
        with tempfile.TemporaryDirectory() as tmp:
            base = Path(tmp) / "ui" / "src"
            write(base, "commonMain/A.kt", text)
            return scan_constructs(module_dir=base)

    def test_system_clock_is_reported(self) -> None:
        hits = self.scan_text("fun a(): Long = System.currentTimeMillis()\n")
        self.assertEqual(len(hits), 1)
        self.assertIn("System", hits[0])

    def test_run_blocking_is_reported(self) -> None:
        hits = self.scan_text("fun a() { runBlocking { } }\n")
        self.assertEqual(len(hits), 1)
        self.assertIn("runBlocking", hits[0])

    def test_synchronized_needs_an_excuse(self) -> None:
        self.assertEqual(len(self.scan_text("fun a() { synchronized(Any()) { } }\n")), 1)

    def test_atomicfu_import_excuses_synchronized(self) -> None:
        text = "import kotlinx.atomicfu.locks.synchronized\nfun a() { synchronized(Any()) { } }\n"
        self.assertEqual(self.scan_text(text), [])

    def test_comment_mentions_do_not_count(self) -> None:
        text = "/** Was System.currentTimeMillis(). */\nfun a(): Long = 0L\n"
        self.assertEqual(self.scan_text(text), [])

    def test_clean_common_code_reports_nothing(self) -> None:
        self.assertEqual(self.scan_text("fun a(): Long = nowMillis()\n"), [])


if __name__ == "__main__":
    unittest.main()
