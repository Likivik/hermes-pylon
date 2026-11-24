#!/usr/bin/env python3
"""Serve the built app, and proxy the API, auth and the websocket to the gateway.

This is the one server for local previews and for the test pod alike. There is deliberately no
second implementation of the same job.

Why this exists: a bare `http.server` can serve the bundle but the app then dials its own
origin for `/api` and `/ws`, finds nothing, and sits in "Reconnecting…" — so no browser test
can assert a connected socket. This makes the preview container behave like the real
deployment: static files from the dist, everything else forwarded.

Environment:
  DIST_ROOT            directory holding index.html and the wasm bundle (default /srv)
  PYLON_PROXY_TARGET   upstream origin, e.g. http://127.0.0.1:9119
  PORT                 listen port (default 8080)
  BIND                 listen address (default 127.0.0.1)
  PROXY_LOG            optional path for one JSON line per request

This server is deliberately host-agnostic: it binds localhost and is configured only by
environment variables. How localhost reaches a phone or another machine — tailscale serve,
an SSH tunnel, a LAN bind — is the caller's business, never this file's.
"""

from __future__ import annotations

import http.server
import json
import os
import re
import socket
import socketserver
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request

DIST_ROOT = os.environ.get("DIST_ROOT", "/srv")
TARGET = urllib.parse.urlsplit(os.environ.get("PYLON_PROXY_TARGET", "http://127.0.0.1:9119"))
TARGET_HOST = TARGET.hostname or "127.0.0.1"
TARGET_PORT = TARGET.port or 80
BIND = os.environ.get("BIND", "127.0.0.1")
PROXY_LOG = os.environ.get("PROXY_LOG") or None
_LOG_LOCK = threading.Lock()

# Paths the app expects to reach the backend on its own origin. Measured from the built bundle:
# the app calls `api/dashboard/...` (covered by /api), `/auth/...`, `/ws` and `/webhook` (25 refs).
# The bare `/dashboard` prefix is NOT used by the app and is not listed: it only ever answered
# `302 -> /login?next=...`, which put the gateway's own sign-in page on this origin.
PROXY_PREFIXES = ("/api", "/auth", "/ws", "/webhook")

HOP_BY_HOP = {
    "connection",
    "keep-alive",
    "proxy-authenticate",
    "proxy-authorization",
    "te",
    "trailers",
    "transfer-encoding",
    "upgrade",
}


def log_event(**fields) -> None:
    """One JSON line per request, so host-side events can be joined with client logs.

    Every response carries `X-Pylon-Request-Id`; when the client sends one it is echoed back, so a
    browser log and this file can be matched. Errors stay diagnosable after the fact.
    """
    if not PROXY_LOG:
        return
    record = {"ts": time.time()}
    record.update({k: v for k, v in fields.items() if v is not None})
    line = json.dumps(record, ensure_ascii=False, default=str)
    with _LOG_LOCK:
        try:
            with open(PROXY_LOG, "a", encoding="utf-8") as fh:
                fh.write(line + "\n")
        except OSError as error:
            sys.stderr.write(f"dev-server: cannot write log: {error}\n")


def cache_control_for(path: str) -> str:
    """Hashed filenames may be cached forever; everything else must not be cached at all.

    `ui.js` and the HTML entry points are not content-hashed, but the wasm is. A browser that keeps a
    cached `ui.js` will pair it with a newer wasm and fail with a confusing load error.
    """
    name = os.path.basename(path.split("?", 1)[0])
    if re.search(r"[0-9a-f]{8,}", os.path.splitext(name)[0]):
        return "public, max-age=31536000, immutable"
    return "no-store"


class Handler(http.server.SimpleHTTPRequestHandler):
    # HTTP/1.1 keeps a connection alive for the browser's next request. The stdlib default of
    # HTTP/1.0 closes after every response, which forces a reconnect per asset.
    protocol_version = "HTTP/1.1"

    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=DIST_ROOT, **kwargs)

    def log_message(self, fmt: str, *args) -> None:
        sys.stderr.write("dev-server: %s\n" % (fmt % args))

    def end_headers(self) -> None:
        # Emitted on every response, including errors and proxied ones.
        if not getattr(self, "request_id", ""):
            incoming = self.headers.get("X-Pylon-Request-Id") if self.headers else None
            self.request_id = incoming or f"host-{int(time.time() * 1000)}"
        self.send_header("X-Pylon-Request-Id", self.request_id)
        # Only our own files get our cache policy. A proxied response already carries the
        # upstream's, and sending a second Cache-Control header would make it ambiguous.
        if not self._is_proxied():
            self.send_header("Cache-Control", cache_control_for(self.path))
        super().end_headers()

    def log_request(self, code: str = "-", size: str = "-") -> None:
        self.request_id = (self.headers.get("X-Pylon-Request-Id") if self.headers else None) or (
            getattr(self, "request_id", "") or f"host-{int(time.time() * 1000)}"
        )
        log_event(
            kind="static" if not self._is_proxied() else "proxy",
            method=self.command,
            path=self.path.split("?", 1)[0],
            status=str(code),
            req=self.request_id,
        )
        super().log_request(code, size)

    def _is_proxied(self) -> bool:
        return self.path.startswith(PROXY_PREFIXES)

    def _is_upgrade(self) -> bool:
        return (self.headers.get("Upgrade") or "").lower() == "websocket"

    def do_GET(self) -> None:
        if self._is_upgrade():
            self._relay_websocket()
        elif self._is_proxied():
            self._proxy()
        else:
            super().do_GET()

    def do_POST(self) -> None:
        self._proxy()

    do_PUT = do_POST
    do_DELETE = do_POST
    do_PATCH = do_POST

    def do_HEAD(self) -> None:
        if self._is_proxied():
            self._proxy()
        else:
            super().do_HEAD()

    def do_OPTIONS(self) -> None:
        self._proxy()

    def _upstream_request(self, body: bytes | None) -> urllib.request.Request:
        url = urllib.parse.urlunsplit(
            (TARGET.scheme, f"{TARGET_HOST}:{TARGET_PORT}", self.path, "", ""),
        )
        headers = {
            name: value
            for name, value in self.headers.items()
            if name.lower() not in HOP_BY_HOP and name.lower() != "host"
        }
        headers["Host"] = f"{TARGET_HOST}:{TARGET_PORT}"
        return urllib.request.Request(url, data=body, headers=headers, method=self.command)

    def _proxy(self) -> None:
        length = int(self.headers.get("Content-Length") or 0)
        body = self.rfile.read(length) if length else None
        try:
            with urllib.request.urlopen(self._upstream_request(body), timeout=30) as upstream:
                payload = upstream.read()
                self.send_response(upstream.status)
                for name, value in upstream.headers.items():
                    if name.lower() not in HOP_BY_HOP:
                        self.send_header(name, value)
                self.send_header("Content-Length", str(len(payload)))
                self.end_headers()
                if self.command != "HEAD":
                    self.wfile.write(payload)
        except urllib.error.HTTPError as error:
            payload = error.read()
            self.send_response(error.code)
            for name, value in error.headers.items():
                if name.lower() not in HOP_BY_HOP:
                    self.send_header(name, value)
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            if self.command != "HEAD":
                self.wfile.write(payload)
        except OSError as error:
            # Say so: an unreachable upstream is a harness fault, not a 404 from the app.
            self.log_message("upstream %s failed: %s", self.path, error)
            self.send_error(502, "upstream unreachable")

    def _relay_websocket(self) -> None:
        # Logged at both ends on purpose: "relay never called" and "relay called, upstream
        # refused the handshake" are opposite bugs, and without these two lines the only
        # symptom is a client stuck on Reconnecting.
        self.log_message("ws relay entry: %s", self.path)
        upstream = socket.create_connection((TARGET_HOST, TARGET_PORT), timeout=30)
        try:
            request = f"GET {self.path} HTTP/1.1\r\n"
            # The dashboard validates Origin against the origin it is reached on; forwarding
            # the preview's own origin gets a 403 before any handshake. Rewrite it to the
            # upstream's, which is what a same-origin deployment would send.
            upstream_origin = f"{TARGET.scheme}://{TARGET_HOST}:{TARGET_PORT}"
            for name, value in self.headers.items():
                if name.lower() == "host":
                    continue
                if name.lower() == "origin":
                    value = upstream_origin
                request += f"{name}: {value}\r\n"
            request += f"Host: {TARGET_HOST}:{TARGET_PORT}\r\n\r\n"
            upstream.sendall(request.encode("latin-1"))

            head = b""
            while b"\r\n\r\n" not in head:
                chunk = upstream.recv(4096)
                if not chunk:
                    self.send_error(502, "upstream closed during handshake")
                    return
                head += chunk
            header, _, early = head.partition(b"\r\n\r\n")
            self.wfile.write(header + b"\r\n\r\n")
            status_line = header.split(b"\r\n", 1)[0].decode("latin-1", "replace")
            self.log_message("ws relay upstream said: %s", status_line)
            # The server's first frame often arrives in the same packet as the handshake.
            # Dropping it desynchronises the protocol and the connection closes at once.
            if early:
                self.log_message("ws relay forwarded %d early bytes", len(early))
                self.wfile.write(early)
            self.wfile.flush()
            if b" 101 " not in header:
                self.log_message("ws relay rejection body: %r", early[:200])

            def upstream_to_client() -> None:
                try:
                    while True:
                        chunk = upstream.recv(65536)
                        if not chunk:
                            self.log_message("ws relay closed by upstream")
                            break
                        self.wfile.write(chunk)
                        self.wfile.flush()
                except OSError as error:
                    self.log_message("ws relay upstream read failed: %s", error)
                finally:
                    self.close_connection = True

            pump = threading.Thread(target=upstream_to_client, daemon=True)
            pump.start()

            # Read the client through `rfile`, not the socket: after the handshake the
            # browser's first frame may already sit in the handler's read buffer, and a
            # select() on the raw socket would never see it — so the gateway waits for a
            # frame that did arrive, times out and closes (measured, run 44022570983).
            while True:
                chunk = self.rfile.read1(65536)
                if not chunk:
                    self.log_message("ws relay closed by client")
                    break
                upstream.sendall(chunk)
        finally:
            upstream.close()


class Server(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True


def main() -> int:
    port = int(os.environ.get("PORT", "8080"))
    if not os.path.isdir(DIST_ROOT):
        sys.stderr.write(f"dev-server: DIST_ROOT {DIST_ROOT} does not exist\n")
        return 2
    print(f"dev-server: serving {DIST_ROOT} on {BIND}:{port}, proxying to {TARGET_HOST}:{TARGET_PORT}")
    if PROXY_LOG:
        print(f"dev-server: request log {PROXY_LOG}")
    with Server((BIND, port), Handler) as server:
        server.serve_forever()
    return 0


if __name__ == "__main__":
    sys.exit(main())
