"""Tests for the dev server.

Every case here is a bug that was actually shipped: an origin answering with another app's document,
a missing file falling through to the gateway, a connection closed mid-sequence, and a response
header that wiped the session. They cost nothing to keep.

Run:  python3 tools/dev-server/test_serve.py
"""

from __future__ import annotations

import http.server
import os
import re
import shutil
import socket
import subprocess
import sys
import tempfile
import threading
import time
import unittest
import urllib.error
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
PROXY = os.path.join(HERE, "serve.py")

WASM_NAME = "41203548298c4f07bacb.wasm"
GATEWAY_MARKER = "GATEWAY-ANSWERED"


class FakeGateway(http.server.BaseHTTPRequestHandler):
    """Stands in for the real gateway. Anything it answers is by definition a proxied request."""

    def log_message(self, *args):
        pass

    def _reply(self):
        length = int(self.headers.get("Content-Length") or 0)
        received = self.rfile.read(length) if length else b""
        # Echo what arrived, so a test can prove the body crossed the proxy and not just that the
        # gateway answered at all.
        body = GATEWAY_MARKER.encode() + b"|" + received
        self.send_response(200)
        self.send_header("Content-Type", "text/plain")
        # A distinctive upstream policy: the proxy must pass it through untouched, never replace or
        # duplicate it with its own.
        self.send_header("Cache-Control", "public, max-age=60")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        self._reply()

    def do_POST(self):
        self._reply()


def free_port() -> int:
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


class DevServerTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.dist = tempfile.mkdtemp(prefix="pylon-dist-")
        with open(os.path.join(cls.dist, "index.html"), "w") as fh:
            fh.write("<html><title>Pylon</title></html>")
        with open(os.path.join(cls.dist, "login.html"), "w") as fh:
            fh.write("<html><title>Sign in</title></html>")
        with open(os.path.join(cls.dist, "ui.js"), "w") as fh:
            fh.write("console.log('pylon');")
        with open(os.path.join(cls.dist, WASM_NAME), "wb") as fh:
            fh.write(b"\x00asm")

        cls.gateway_port = free_port()
        cls.gateway = http.server.ThreadingHTTPServer(
            ("127.0.0.1", cls.gateway_port), FakeGateway
        )
        threading.Thread(target=cls.gateway.serve_forever, daemon=True).start()

        cls.port = free_port()
        cls.log = os.path.join(cls.dist, "requests.jsonl")
        env = dict(os.environ)
        env.update(
            DIST_ROOT=cls.dist,
            PYLON_PROXY_TARGET=f"http://127.0.0.1:{cls.gateway_port}",
            PORT=str(cls.port),
            BIND="127.0.0.1",
            PROXY_LOG=cls.log,
        )
        cls.proc = subprocess.Popen(
            [sys.executable, PROXY], env=env,
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        )
        deadline = time.time() + 15
        while time.time() < deadline:
            try:
                with socket.create_connection(("127.0.0.1", cls.port), timeout=0.4):
                    break
            except OSError:
                time.sleep(0.1)
        else:
            raise RuntimeError("dev server did not start")

    @classmethod
    def tearDownClass(cls):
        cls.proc.terminate()
        cls.proc.wait(timeout=10)
        cls.gateway.shutdown()
        shutil.rmtree(cls.dist, ignore_errors=True)

    def fetch(self, path, method="GET", data=None):
        req = urllib.request.Request(
            f"http://127.0.0.1:{self.port}{path}", data=data, method=method
        )
        try:
            with urllib.request.urlopen(req, timeout=15) as resp:
                # Keep the Message object: its .get() is case-insensitive. dict() would make
                # Content-Type look absent when the server spells it Content-type.
                return resp.status, resp.headers, resp.read()
        except urllib.error.HTTPError as exc:
            return exc.code, exc.headers, exc.read()

    # --- the bundle is served as itself, never as another app's document -------------------

    def test_bundle_files_are_served_not_proxied(self):
        for path in ("/", "/index.html", "/login.html", "/ui.js"):
            status, _h, body = self.fetch(path)
            self.assertEqual(200, status, path)
            self.assertNotIn(GATEWAY_MARKER.encode(), body, path)

    def test_wasm_keeps_its_mime_type(self):
        """Mobile browsers reject WebAssembly.instantiateStreaming on a wrong MIME type."""
        status, headers, _b = self.fetch(f"/{WASM_NAME}")
        self.assertEqual(200, status)
        self.assertEqual("application/wasm", headers.get("Content-Type"))

    def test_a_query_string_does_not_hide_a_real_file(self):
        status, _h, body = self.fetch("/ui.js?cachebust=1")
        self.assertEqual(200, status)
        self.assertNotIn(GATEWAY_MARKER.encode(), body)

    # --- the gateway's own pages must never appear on this origin -------------------------

    def test_unknown_paths_never_reach_the_gateway(self):
        """The gateway answers /login.html with its own dashboard. It must be unreachable here."""
        for path in ("/nothing-here", "/__diag_probe.txt", "/gone.js", "/assets/react-vendor-abc.js"):
            status, _h, body = self.fetch(path)
            self.assertNotIn(GATEWAY_MARKER.encode(), body, path)
            self.assertEqual(404, status, path)

    def test_bare_login_path_does_not_reach_the_gateway(self):
        status, _h, body = self.fetch("/login")
        self.assertNotIn(GATEWAY_MARKER.encode(), body)

    # --- the API must still get through ----------------------------------------------------

    def test_api_and_auth_routes_reach_the_gateway(self):
        for path in ("/api/status", "/api/profiles", "/auth/password-login"):
            status, _h, body = self.fetch(path)
            self.assertEqual(200, status, path)
            self.assertIn(GATEWAY_MARKER.encode(), body, path)

    def test_a_post_body_reaches_the_gateway_intact(self):
        """This is the login path."""
        payload = b"password=hunter2&next=%2F"
        status, _h, body = self.fetch(
            "/auth/password-login", method="POST", data=payload
        )
        self.assertEqual(200, status)
        self.assertIn(GATEWAY_MARKER.encode(), body)
        self.assertIn(payload, body, "the body must survive the proxy byte for byte")

    # --- one connection must carry many requests -------------------------------------------

    def _sequence_over_one_connection(self):
        """The browser's real pattern: page, API, POST, then more pages on the same socket."""
        steps = [
            (b"GET /login.html HTTP/1.1\r\nHost: h\r\nAccept: text/html\r\nConnection: keep-alive\r\n\r\n", "/login.html"),
            (b"GET /api/status HTTP/1.1\r\nHost: h\r\nAccept: */*\r\nConnection: keep-alive\r\n\r\n", "/api/status"),
            (b"POST /auth/password-login HTTP/1.1\r\nHost: h\r\nContent-Length: 16\r\n"
             b"Content-Type: application/x-www-form-urlencoded\r\nConnection: keep-alive\r\n\r\npassword=test123",
             "/auth/password-login"),
            (b"GET /login.html HTTP/1.1\r\nHost: h\r\nAccept: text/html\r\nConnection: keep-alive\r\n\r\n", "/login.html"),
            (b"GET /index.html HTTP/1.1\r\nHost: h\r\nAccept: text/html\r\nConnection: keep-alive\r\n\r\n", "/index.html"),
        ]
        out = []
        sock = socket.create_connection(("127.0.0.1", self.port), timeout=25)
        try:
            for raw, label in steps:
                sock.sendall(raw)
                buf = b""
                while b"\r\n\r\n" not in buf:
                    chunk = sock.recv(65536)
                    if not chunk:
                        out.append((label, b"", b""))
                        break
                    buf += chunk
                else:
                    head, rest = buf.split(b"\r\n\r\n", 1)
                    m = re.search(rb"content-length:\s*(\d+)", head, re.I)
                    n = int(m.group(1)) if m else 0
                    while len(rest) < n:
                        chunk = sock.recv(65536)
                        if not chunk:
                            break
                        rest += chunk
                    out.append((label, head, rest[:n]))
        finally:
            sock.close()
        return out

    def test_one_connection_serves_every_request_to_the_right_app(self):
        for trial in range(2):
            for label, head, body in self._sequence_over_one_connection():
                self.assertTrue(head, f"trial {trial}: connection closed on {label}")
                if label.endswith(("login.html", "index.html")):
                    self.assertNotIn(
                        GATEWAY_MARKER.encode(), body,
                        f"trial {trial}: {label} answered with the gateway's document",
                    )

    # --- cache policy ----------------------------------------------------------------------

    def test_unhashed_files_are_never_cached(self):
        """ui.js and the HTML entry points are not content-hashed. A cached copy points at a wasm
        that no longer exists and fails with a confusing load error."""
        for path in ("/ui.js", "/index.html", "/login.html"):
            _s, headers, _b = self.fetch(path)
            self.assertIn("no-store", headers.get("Cache-Control", ""), path)

    def test_hashed_assets_may_be_cached_forever(self):
        """The wasm filename carries its content hash, so a long cache is safe and saves a 47MB
        download on every reload."""
        _s, headers, _b = self.fetch(f"/{WASM_NAME}")
        self.assertIn("immutable", headers.get("Cache-Control", ""))

    def test_a_proxied_response_keeps_the_upstream_cache_policy(self):
        """We must not replace or duplicate somebody else's Cache-Control header."""
        _s, headers, _b = self.fetch("/api/status")
        seen = headers.get_all("Cache-Control") or []
        self.assertEqual(1, len(seen), f"expected exactly one upstream header, got {seen}")
        self.assertEqual("public, max-age=60", seen[0])

    # --- headers carry real values ---------------------------------------------------------

    def test_request_id_is_a_real_id_not_a_format_placeholder(self):
        """A lost f-prefix once made this print the literal braces."""
        _s, headers, _b = self.fetch("/ui.js")
        request_id = headers.get("X-Pylon-Request-Id", "")
        self.assertTrue(request_id)
        self.assertNotIn("{request_id}", request_id)

    def test_every_request_is_logged(self):
        self.fetch("/ui.js")
        deadline = time.time() + 5
        while time.time() < deadline:
            if os.path.exists(self.log) and "/ui.js" in open(self.log, encoding="utf-8").read():
                return
            time.sleep(0.1)
        self.fail("the request was not written to PROXY_LOG")


if __name__ == "__main__":
    unittest.main(verbosity=2)
