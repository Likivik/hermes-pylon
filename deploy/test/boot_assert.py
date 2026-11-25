#!/usr/bin/env python3
"""Assert that a served Pylon bundle can actually boot.

The failure this exists for: `ui.js` references more than one wasm chunk name, and a deploy
that shipped only the newest one left the browser fetching HTML for the missing chunk, which
surfaces as `WebAssembly.instantiate(): expected magic word 00 61 73 6d, found 3c 21 64 6f` —
indistinguishable, to a person, from a corrupt build.

So the check is deliberately dumb and static: fetch the entry, fetch the loader, read every wasm
chunk the loader names, and require each one to answer 200 with `application/wasm` and the
four magic bytes. No browser, no CDP, no flake.

    python3 deploy/test/boot_assert.py --base http://127.0.0.1:8080

Exit 0 when the bundle can boot, 1 with a named reason when it cannot.
"""

from __future__ import annotations

import argparse
import re
import sys
import urllib.error
import urllib.request

WASM_MAGIC = b"\x00asm"
CHUNK = re.compile(r"[0-9a-f]{16,32}\.wasm")
TIMEOUT = 20


def fetch(url: str) -> tuple[int, bytes, str]:
    try:
        with urllib.request.urlopen(url, timeout=TIMEOUT) as response:
            return response.status, response.read(), response.headers.get("content-type", "")
    except urllib.error.HTTPError as error:
        return error.code, b"", error.headers.get("content-type", "") if error.headers else ""
    except urllib.error.URLError as error:
        raise SystemExit(f"cannot reach {url}: {error.reason}") from error


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://127.0.0.1:8080")
    parser.add_argument("--chunk-limit", type=int, default=0, help="0 = check every referenced chunk")
    args = parser.parse_args(argv)
    base = args.base.rstrip("/")

    status, index, _ = fetch(f"{base}/")
    if status not in (200, 302):
        print(f"FAIL index {base}/ answered {status}", file=sys.stderr)
        return 1

    status, loader, ctype = fetch(f"{base}/ui.js")
    if status != 200:
        print(f"FAIL ui.js answered {status}", file=sys.stderr)
        return 1
    if b"wasm" not in loader and b"ui.js" not in loader:
        print("FAIL ui.js does not look like the bundle loader", file=sys.stderr)
        return 1

    chunks = sorted(set(CHUNK.findall(loader.decode("utf-8", errors="replace"))))
    if not chunks:
        print("FAIL ui.js names no wasm chunk", file=sys.stderr)
        return 1
    if args.chunk_limit:
        chunks = chunks[: args.chunk_limit]

    failures: list[str] = []
    for name in chunks:
        status, body, ctype = fetch(f"{base}/{name}")
        if status != 200:
            failures.append(f"{name}: HTTP {status}")
            continue
        if not body.startswith(WASM_MAGIC):
            failures.append(f"{name}: not wasm (first bytes {body[:4]!r}) served as {ctype or 'unknown'}")
            continue
        if "wasm" not in ctype:
            failures.append(f"{name}: 200 but content-type {ctype or 'unknown'}")

    if failures:
        for failure in failures:
            print(f"FAIL {failure}", file=sys.stderr)
        print(f"bundle cannot boot: {len(failures)} of {len(chunks)} chunks failed", file=sys.stderr)
        return 1

    print(f"ok: {len(chunks)} wasm chunk(s) served, loader {len(loader)} bytes")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
