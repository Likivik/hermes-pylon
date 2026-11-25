#!/usr/bin/env python3
"""Copy a built dev bundle into a serving root without half-shipping it.

The failure this exists for: the webpack loader names more than one wasm chunk, the build
directory kept an older artifact, and a copy that took "the newest wasm" left the loader asking
for a chunk that was not there — the browser fetched HTML and aborted with a wasm magic-word
error that looks exactly like a corrupt build.

So the unit of copy is *what the loader asks for*, never "the newest file":

  1. read `ui.js`, collect every `<hash>.wasm` it names;
  2. fail before touching the root if any of them is missing from the build;
  3. clear stale bundles from the root (old chunks included);
  4. copy entry, loader, every named chunk, and the resource directories;
  5. verify every copied chunk starts with the wasm magic bytes.

    python3 deploy/dev/deploy_dev_bundle.py \
        --src ui/build/kotlin-webpack/wasmJs/developmentExecutable \
        --dst /tmp/pylon-dev-root

Then run `deploy/test/boot_assert.py --base <url>` against the server that serves `--dst`.
"""

from __future__ import annotations

import argparse
import re
import shutil
import sys
from pathlib import Path

CHUNK = re.compile(r"[0-9a-f]{16,32}\.wasm")
MAGIC = b"\x00asm"
# Directories the bundle needs beside the loader, when the build produced them.
RESOURCE_DIRS = ("composeResources",)
RESOURCE_GLOBS = ("*.png", "*.svg", "*.ico", "*.json", "*.css")


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--src", required=True, type=Path)
    parser.add_argument("--dst", required=True, type=Path)
    # The development webpack task emits only ui.js + chunks; the entry page lives in resources.
    parser.add_argument("--index", type=Path, default=Path("ui/src/wasmJsMain/resources/index.html"))
    parser.add_argument("--resources", type=Path, default=Path("ui/src/wasmJsMain/resources"))
    args = parser.parse_args(argv)

    src: Path = args.src
    dst: Path = args.dst
    loader_path = src / "ui.js"
    index_path = args.index if args.index.is_file() else src / "index.html"
    for required in (loader_path, index_path):
        if not required.is_file():
            print(f"FAIL missing {required}", file=sys.stderr)
            return 1

    loader = loader_path.read_text(encoding="utf-8", errors="replace")
    chunks = sorted(set(CHUNK.findall(loader)))
    if not chunks:
        print(f"FAIL {loader_path} names no wasm chunk", file=sys.stderr)
        return 1

    missing = [name for name in chunks if not (src / name).is_file()]
    if missing:
        # Nothing has been touched yet: the serving root still holds the previous, working build.
        print(f"FAIL loader names chunks the build does not have: {', '.join(missing)}", file=sys.stderr)
        return 1

    dst.mkdir(parents=True, exist_ok=True)
    # Stale bundles: any old chunk, loader or entry left behind could be requested again.
    for stale in list(dst.glob("*.wasm")) + list(dst.glob("*.wasm.gz")) + list(dst.glob("*.wasm.br")):
        stale.unlink()
    for name in ("ui.js", "index.html", "ui.js.LICENSE.txt"):
        (dst / name).unlink(missing_ok=True)

    shutil.copy2(index_path, dst / "index.html")
    shutil.copy2(loader_path, dst / "ui.js")
    for name in chunks:
        shutil.copy2(src / name, dst / name)

    for directory in RESOURCE_DIRS:
        if (src / directory).is_dir():
            target = dst / directory
            if target.exists():
                shutil.rmtree(target)
            shutil.copytree(src / directory, target)
    for pattern in RESOURCE_GLOBS:
        for root in (src, args.resources):
            if not root.is_dir():
                continue
            for item in root.glob(pattern):
                if item.is_file() and item.name != "index.html":
                    shutil.copy2(item, dst / item.name)

    bad = [name for name in chunks if not (dst / name).read_bytes()[:4] == MAGIC]
    if bad:
        print(f"FAIL copied chunks are not wasm: {', '.join(bad)}", file=sys.stderr)
        return 1

    total = sum((dst / name).stat().st_size for name in chunks)
    print(f"deployed {len(chunks)} chunk(s), {total / 1_048_576:.1f} MiB, into {dst}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
