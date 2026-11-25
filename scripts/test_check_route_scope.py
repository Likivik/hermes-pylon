#!/usr/bin/env python3
"""Tests for check_route_scope.py — a guard is only worth having if it fails.

Each case points the guard at a miniature client and inventory in a temp dir.
"""

from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

import check_route_scope as guard

CLIENT = '''
class Api {
    suspend fun getSession(id: String) = get("api/sessions/$id")
    suspend fun prune() = post("api/sessions/prune", body = x)
    suspend fun newThing() = post("api/new/thing", body = x)
}
'''

INVENTORY = {
    "gateway": "test",
    "routes": [
        {"method": "GET", "path": "/api/sessions/{session_id}", "scope": "query"},
        {"method": "POST", "path": "/api/sessions/prune", "scope": "body"},
    ],
}


class RouteScopeTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory()
        root = Path(self.tmp.name)
        self.client = root / "Api.kt"
        self.inventory = root / "route_scopes.json"
        self.client.write_text(CLIENT)
        self.inventory.write_text(json.dumps(INVENTORY))
        self._api, self._inv = guard.API, guard.INVENTORY
        guard.API, guard.INVENTORY = self.client, self.inventory

    def tearDown(self) -> None:
        guard.API, guard.INVENTORY = self._api, self._inv
        self.tmp.cleanup()

    def test_normalise_treats_parameter_names_as_one_shape(self) -> None:
        self.assertEqual(guard.normalise("api/sessions/$id"), guard.normalise("/api/sessions/{session_id}"))

    def test_classified_paths_pass(self) -> None:
        self.client.write_text('fun a() = get("api/sessions/$id")'.replace("$", "\u0024"))
        self.assertEqual(0, guard.main())

    def test_an_unclassified_client_path_fails(self) -> None:
        self.assertEqual(1, guard.main())

    def test_a_body_scoped_route_needs_a_model_carrying_profile(self) -> None:
        self.inventory.write_text(
            json.dumps(
                {"gateway": "test", "routes": [{"method": "POST", "path": "/api/x/body", "scope": "body"}]}
            )
        )
        self.client.write_text('    suspend fun send(body: UnscopedRequest) = post("api/x/body", body)')

        self.assertEqual(1, guard.main())

    def test_a_missing_inventory_is_not_a_pass(self) -> None:
        self.inventory.unlink()
        self.assertEqual(2, guard.main())


if __name__ == "__main__":
    unittest.main()
