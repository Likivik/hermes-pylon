#!/usr/bin/env python3
"""Dev server for the Pylon wasm bundle, plus an access log.

Why this exists: the wasm build treats the page origin as the gateway
(`GatewayPlatformWasmJs`), so `/api/*` and the chat WebSocket have to be served
from the same origin as index.html. This serves the bundle, proxies everything
that is not a file on disk to the gateway, and writes one JSON line per request
so the host side can be joined with client logs on `X-Pylon-Request-Id`.

Usage:
    python3 tools/dev-proxy/serve_pylon_web.py \
        --root ui/build/kotlin-webpack/wasmJs/developmentExecutable \
        --resources ui/build/processedResources/wasmJs/main \
        --gateway 127.0.0.1:8787 \
        --port 8080 \
        --log /var/log/pylon-proxy.jsonl

The gateway is expected to log (or at least echo) `X-Pylon-Request-Id`; the same
header appears in the client's diagnostics screen, which is what makes a report
like "it timed out at 14:02" resolvable with one grep.
"""
import argparse
import asyncio
import json
import mimetypes
import os
import shutil
import time

mimetypes.add_type("application/wasm", ".wasm")
mimetypes.add_type("text/javascript", ".js")
mimetypes.add_type("text/javascript", ".mjs")

ARGS = None


def log_event(**fields):
    if not ARGS.log:
        return
    line = json.dumps({"ts": round(time.time(), 3), **fields}, ensure_ascii=False)
    with open(ARGS.log, "a", encoding="utf-8") as fh:
        fh.write(line + "\n")


def is_static(path):
    rel = path.split("?", 1)[0].lstrip("/") or "index.html"
    full = os.path.normpath(os.path.join(ARGS.root, rel))
    return full.startswith(ARGS.root) and os.path.isfile(full)


async def serve_static(path, writer, request_id, started):
    rel = path.split("?", 1)[0].lstrip("/") or "index.html"
    full = os.path.normpath(os.path.join(ARGS.root, rel))
    with open(full, "rb") as fh:
        data = fh.read()
    ctype = mimetypes.guess_type(full)[0] or "application/octet-stream"
    writer.write(
        f"HTTP/1.1 200 OK\r\nContent-Type: {ctype}\r\nContent-Length: {len(data)}\r\n"
        f"Cache-Control: no-cache\r\nX-Pylon-Request-Id: {request_id}\r\n\r\n".encode()
    )
    writer.write(data)
    await writer.drain()
    writer.close()
    log_event(kind="static", path=rel, status=200, ms=int((time.time() - started) * 1000), req=request_id)


async def pipe(reader, writer):
    try:
        while True:
            chunk = await reader.read(65536)
            if not chunk:
                break
            writer.write(chunk)
            await writer.drain()
    except (ConnectionResetError, BrokenPipeError, asyncio.CancelledError):
        pass
    finally:
        try:
            writer.close()
        except Exception:
            pass


async def handle(client_reader, client_writer):
    started = time.time()
    try:
        head = await client_reader.readuntil(b"\r\n\r\n")
    except (asyncio.IncompleteReadError, ConnectionResetError):
        client_writer.close()
        return
    try:
        request_line = head.split(b"\r\n", 1)[0].decode("latin-1")
        method, path, _ = request_line.split(" ", 2)
    except ValueError:
        client_writer.close()
        return

    request_id = ""
    for line in head.decode("latin-1").split("\r\n")[1:]:
        if line.lower().startswith("x-pylon-request-id:"):
            request_id = line.split(":", 1)[1].strip()
    request_id = request_id or f"host-{int(started * 1000)}"

    if is_static(path):
        await serve_static(path, client_writer, request_id, started)
        return

    decl = head.decode("latin-1").split("\r\n")
    kept = [decl[0]] + [h for h in decl[1:] if h and not h.lower().startswith(("host:", "connection:"))]
    forwarded = "\r\n".join(
        kept + [f"Host: {ARGS.gateway}", "Connection: keep-alive", "", ""]
    ).encode("latin-1")

    try:
        up_reader, up_writer = await asyncio.open_connection(*ARGS.gateway.split(":"))
    except OSError as exc:
        log_event(kind="proxy", path=path, status="connect_failed", error=str(exc), req=request_id)
        client_writer.write(b"HTTP/1.1 502 Bad Gateway\r\nContent-Length: 0\r\n\r\n")
        await client_writer.drain()
        client_writer.close()
        return

    up_writer.write(forwarded)
    if b"content-length" in head.lower() or b"transfer-encoding" in head.lower():
        try:
            leftover = await client_reader.read(65536)
        except Exception:
            leftover = b""
        if leftover:
            up_writer.write(leftover)
    await up_writer.drain()

    await asyncio.gather(pipe(client_reader, up_writer), pipe(up_reader, client_writer))
    log_event(
        kind="proxy",
        method=method,
        path=path.split("?", 1)[0],
        ms=int((time.time() - started) * 1000),
        req=request_id,
    )


async def main():
    global ARGS
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", required=True, help="webpack output directory")
    parser.add_argument("--resources", help="processedResources dir to overlay (index.html, compose resources)")
    parser.add_argument("--gateway", default="127.0.0.1:8787", help="host:port of the Hermes gateway")
    parser.add_argument("--port", type=int, default=8080)
    parser.add_argument("--bind", default="0.0.0.0")
    parser.add_argument("--log", default="", help="JSONL access log path")
    ARGS = parser.parse_args()

    if ARGS.resources:
        for name in os.listdir(ARGS.resources):
            src = os.path.join(ARGS.resources, name)
            dst = os.path.join(ARGS.root, name)
            if os.path.isdir(src):
                shutil.copytree(src, dst, dirs_exist_ok=True)
            else:
                shutil.copy2(src, dst)

    server = await asyncio.start_server(handle, ARGS.bind, ARGS.port)
    print(f"serving {ARGS.root} on {ARGS.bind}:{ARGS.port}, proxy -> {ARGS.gateway}, log -> {ARGS.log or 'off'}", flush=True)
    async with server:
        await server.serve_forever()


if __name__ == "__main__":
    asyncio.run(main())
