"""Hermetic tests; no network, credentials, Gradle, or GitHub mutations."""
import importlib.util
import os
import pathlib
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location(
    "monitor", pathlib.Path(__file__).with_name("maintenance-monitor.py")
)
assert SPEC is not None and SPEC.loader is not None
monitor = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(monitor)
A = "a" * 40
B = "b" * 40


class MonitorTests(unittest.TestCase):
    def test_lock_contention_and_stale_owner_policy(self):
        with tempfile.TemporaryDirectory(dir=os.environ["TMPDIR"]) as temp:
            path = pathlib.Path(temp) / "maintenance.lock"
            first = monitor.MaintenanceLock(path)
            second = monitor.MaintenanceLock(path)
            first.acquire()
            with self.assertRaises(monitor.MonitorError):
                second.acquire()
            first.release()
            second.acquire()
            # A dead process or malformed metadata is never auto-reaped.
            (path / "owner.json").write_text('{"uuid":"stale","pid":0}')
            with self.assertRaises(monitor.MonitorError):
                second.release()
            with self.assertRaises(monitor.MonitorError):
                first.acquire()
            self.assertTrue(path.exists())

    def test_changes_compare_content_not_clock(self):
        before = {
            "fork_head": A, "upstream_head": A,
            "public_advisory_ids": ["GHSA-abcd-efgh-ijkl"],
        }
        self.assertEqual(monitor.compare(before, dict(before)), {
            "fork_changed": False, "upstream_changed": False,
            "new_public_advisory_ids": [], "removed_public_advisory_ids": [],
        })
        after = {
            "fork_head": A, "upstream_head": B,
            "public_advisory_ids": ["GHSA-new1-new2-new3"],
        }
        result = monitor.compare(before, after)
        self.assertTrue(result["upstream_changed"])
        self.assertFalse(result["fork_changed"])
        self.assertEqual(
            result["new_public_advisory_ids"], ["GHSA-new1-new2-new3"]
        )
        self.assertEqual(
            result["removed_public_advisory_ids"], ["GHSA-abcd-efgh-ijkl"]
        )

    def test_api_errors_and_malformed_data_fail_closed(self):
        with patch.object(
            monitor, "command", side_effect=monitor.MonitorError("API failure")
        ):
            with self.assertRaises(monitor.MonitorError):
                monitor.snapshot("owner/fork", "owner/upstream")
        with patch.object(monitor, "api", side_effect=[
            {"object": {"sha": A}}, {"object": {"sha": B}}, [{}]
        ]):
            with self.assertRaises(monitor.MonitorError):
                monitor.snapshot("owner/fork", "owner/upstream")
        with patch.object(monitor, "api", side_effect=[
            {"object": {"sha": A}}, {"object": {"sha": B}}, [{}] * 100
        ]):
            with self.assertRaises(monitor.MonitorError):
                monitor.snapshot("owner/fork", "owner/upstream")

    def test_no_external_error_details(self):
        with patch.object(monitor.subprocess, "run") as run:
            run.return_value.returncode = 1
            run.return_value.stderr = "token=secret"
            with self.assertRaises(monitor.MonitorError) as failure:
                monitor.command("gh", "api", "example")
            self.assertNotIn("secret", str(failure.exception))

    def test_compare_rejects_malformed_snapshot_on_either_side(self):
        valid = {
            "fork_head": A, "upstream_head": B,
            "public_advisory_ids": ["GHSA-abcd-efgh-ijkl"],
        }
        for key, bad in (
            ("fork_head", "invalid"),
            ("upstream_head", None),
            ("public_advisory_ids", None),
            ("public_advisory_ids", ["bad-id"]),
            ("public_advisory_ids", [42]),
        ):
            for side in ("before", "after"):
                with self.subTest(key=key, bad=bad, side=side):
                    malformed = {**valid, key: bad}
                    args = (malformed, valid) if side == "before" else (
                        valid, malformed
                    )
                    with self.assertRaises(monitor.MonitorError):
                        monitor.compare(*args)


if __name__ == "__main__":
    unittest.main()
