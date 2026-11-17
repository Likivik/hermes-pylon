#!/usr/bin/env python3
"""Serve the built app, and proxy the API, auth and the websocket to the dashboard.

Why this exists: a bare `http.server` can serve the bundle but the app then dials its own
origin for `/api` and `/ws`, finds nothing, and sits in "Reconnecting…" — so no browser test
can assert a connected socket. This makes the preview container behave like the real
deployment: static files from the dist, everything else forwarded.

Environment:
  DIST_ROOT            directory holding index.html and the wasm bundle (default /srv)
  PYLON_PROXY_TARGET   upstream origin, e.g. http://127.0.0.1:9119
"""

from __future__ import annotations

import http.server
import os
import select
import socket
import socketserver
import sys
import urllib.error
import urllib.parse
import urllib.request

DIST_ROOT = os.environ.get("DIST_ROOT", "/srv")
TARGET = urllib.parse.urlsplit(os.environ.get("PYLON_PROXY_TARGET", "http://127.0.0.1:9119"))
TARGET_HOST = TARGET.hostname or "127.0.0.1"
TARGET_PORT = TARGET.port or 80

# Paths the app expects to reach the backend on its own origin.
PROXY_PREFIXES = ("/api", "/auth", "/ws", "/webhook", "/dashboard")

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


class Handler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=DIST_ROOT, **kwargs)

    def log_message(self, fmt: str, *args) -> None:
        sys.stderr.write("dist-proxy: %s\n" % (fmt % args))

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

            sockets = [self.connection, upstream]
            while True:
                readable, _, errored = select.select(sockets, [], sockets, 60)
                if errored:
                    return
                if not readable:
                    continue  # idle websocket: keep it open
                for source in readable:
                    data = source.recv(65536)
                    if not data:
                        self.log_message(
                            "ws relay closed by %s",
                            "client" if source is self.connection else "upstream",
                        )
                        return
                    (upstream if source is self.connection else self.connection).sendall(data)
        finally:
            upstream.close()


class Server(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True


def main() -> int:
    port = int(os.environ.get("PORT", "8080"))
    if not os.path.isdir(DIST_ROOT):
        sys.stderr.write(f"dist-proxy: DIST_ROOT {DIST_ROOT} does not exist\n")
        return 2
    print(f"dist-proxy: serving {DIST_ROOT} on :{port}, proxying to {TARGET_HOST}:{TARGET_PORT}")
    with Server(("0.0.0.0", port), Handler) as server:
        server.serve_forever()
    return 0


if __name__ == "__main__":
    sys.exit(main())
