#!/usr/bin/env python3
"""A fixed OpenAI-compatible completion endpoint, for seeding sessions and logs offline.

The gateway will not create a session except by running a conversation, and its logs only
exist once it writes them. Pointing the test profile's model at this stub produces both by
using the product, instead of reaching into state.db.

Deliberately dumb: one canned reply, no streaming deltas beyond a single chunk, no tool
calls. It exists to make the sessions and logs screens have content, not to test models.

    python3 stub_provider.py --port 19200
"""

from __future__ import annotations

import argparse
import json
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

REPLY = (
    "Seeded reply. This text comes from the local stub provider, so it is identical on every run.\n\n"
    "Build finished. Gateway check: ok."
)

MODEL = "stub-1"


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):  # keep the harness output readable
        pass

    def _send(self, code: int, payload: dict) -> None:
        body = json.dumps(payload).encode()
        self.send_response(code)
        self.send_header("content-type", "application/json")
        self.send_header("content-length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):  # noqa: N802 - http.server naming
        if self.path.rstrip("/") in ("/v1/models", "/models"):
            self._send(200, {"object": "list", "data": [{"id": MODEL, "object": "model"}]})
            return
        if self.path.rstrip("/") in ("/health", "/v1/health"):
            self._send(200, {"status": "ok"})
            return
        self._send(404, {"error": {"message": f"no route {self.path}"}})

    def do_POST(self):  # noqa: N802
        length = int(self.headers.get("content-length") or 0)
        raw = self.rfile.read(length) if length else b"{}"
        try:
            request = json.loads(raw)
        except json.JSONDecodeError:
            self._send(400, {"error": {"message": "body is not json"}})
            return

        if self.path.rstrip("/") not in ("/v1/chat/completions", "/chat/completions"):
            self._send(404, {"error": {"message": f"no route {self.path}"}})
            return

        now = int(time.time())
        completion_id = "chatcmpl-stub-0001"

        if request.get("stream"):
            self.send_response(200)
            self.send_header("content-type", "text/event-stream")
            self.send_header("cache-control", "no-cache")
            self.send_header("connection", "close")
            self.end_headers()
            chunk = {
                "id": completion_id,
                "object": "chat.completion.chunk",
                "created": now,
                "model": MODEL,
                "choices": [{"index": 0, "delta": {"role": "assistant", "content": REPLY}, "finish_reason": None}],
            }
            stop = {
                "id": completion_id,
                "object": "chat.completion.chunk",
                "created": now,
                "model": MODEL,
                "choices": [{"index": 0, "delta": {}, "finish_reason": "stop"}],
            }
            self.wfile.write(f"data: {json.dumps(chunk)}\n\n".encode())
            self.wfile.write(f"data: {json.dumps(stop)}\n\n".encode())
            self.wfile.write(b"data: [DONE]\n\n")
            self.wfile.flush()
            return

        self._send(
            200,
            {
                "id": completion_id,
                "object": "chat.completion",
                "created": now,
                "model": MODEL,
                "choices": [
                    {
                        "index": 0,
                        "message": {"role": "assistant", "content": REPLY},
                        "finish_reason": "stop",
                    }
                ],
                "usage": {"prompt_tokens": 12, "completion_tokens": len(REPLY.split()), "total_tokens": 12 + len(REPLY.split())},
            },
        )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=19200)
    args = parser.parse_args()
    server = ThreadingHTTPServer((args.host, args.port), Handler)
    print(f"stub provider on http://{args.host}:{args.port}/v1", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
