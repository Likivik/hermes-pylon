#!/usr/bin/env python3
"""Tests for localized-resource placeholder validation."""

from __future__ import annotations

import contextlib
import importlib.util
import io
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from xml.etree import ElementTree

SCRIPT = Path(__file__).with_name("check-localized-resources.py")
SPEC = importlib.util.spec_from_file_location("check_localized_resources", SCRIPT)
assert SPEC and SPEC.loader
checker = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(checker)


class PlaceholderTests(unittest.TestCase):
    def test_unindexed_arguments_preserve_order(self) -> None:
        self.assertTrue(checker.placeholders_match("%s: %d", "%s：%d"))
        self.assertFalse(checker.placeholders_match("%s: %d", "%d：%s"))

    def test_indexed_arguments_may_reorder(self) -> None:
        self.assertTrue(checker.placeholders_match("%1$s: %2$d", "%2$d：%1$s"))

    def test_literal_percent_is_not_an_argument(self) -> None:
        self.assertTrue(checker.placeholders_match("%1$d%%", "%1$d%%"))

    def test_missing_argument_fails(self) -> None:
        self.assertFalse(checker.placeholders_match("%1$s: %2$d", "%1$s"))

    def test_date_time_suffix_must_match(self) -> None:
        self.assertTrue(checker.placeholders_match("%1$tY", "%1$tY"))
        self.assertFalse(checker.placeholders_match("%1$tY", "%1$tm"))


class ArabicResourceTests(unittest.TestCase):
    def test_arabic_catalog_is_downstream_subset_with_matching_arguments(self) -> None:
        default = checker.strings(checker.RESOURCE_ROOT / "values/strings.xml")
        arabic = checker.strings(checker.RESOURCE_ROOT / "values-ar/strings.xml")
        self.assertIn("language_arabic", arabic)
        self.assertIn("sessions_fork_indicator", arabic)
        self.assertGreater(len(arabic), 700)
        self.assertLess(len(arabic), len(default))  # English fallback is intentional.
        self.assertFalse(arabic.keys() - default.keys())
        self.assertFalse(any(key.startswith("update_") for key in arabic))
        for name, value in arabic.items():
            with self.subTest(name=name):
                self.assertTrue(checker.placeholders_match(default[name], value))

    def test_arabic_plural_categories_keep_count_argument(self) -> None:
        resources = ElementTree.parse(checker.RESOURCE_ROOT / "values-ar/strings.xml").getroot()
        default = ElementTree.parse(checker.RESOURCE_ROOT / "values/strings.xml").getroot()
        allowed = {item.get("name") for item in default.findall("plurals")}
        for plural in resources.findall("plurals"):
            self.assertIn(plural.get("name"), allowed)
            self.assertEqual({"zero", "one", "two", "few", "many", "other"},
                             {item.get("quantity") for item in plural})
            for item in plural:
                self.assertTrue(checker.placeholders_match("%1$d", item.text or ""))

    def test_sparse_locale_allows_fallback_but_rejects_extras_and_bad_arguments(self) -> None:
        with tempfile.TemporaryDirectory(dir=os.environ.get("TMPDIR")) as directory:
            root = Path(directory)
            (root / "values").mkdir()
            (root / "values-ar").mkdir()
            (root / "values/strings.xml").write_text(
                '<resources><string name="count">%1$d</string>'
                '<string name="fallback">English</string></resources>')
            path = root / "values-ar/strings.xml"
            with patch.object(checker, "RESOURCE_ROOT", root), patch.object(checker, "LOCALES", ()):
                for xml, expected in [
                    ('<string name="count">%1$d</string>', 0),
                    ('<string name="count">%1$s</string>', 1),
                    ('<string name="extra">غير موجود</string>', 1),
                ]:
                    path.write_text('<resources>' + xml + '</resources>')
                    with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
                        self.assertEqual(expected, checker.main())


if __name__ == "__main__":
    unittest.main()
