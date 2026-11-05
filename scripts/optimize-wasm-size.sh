#!/usr/bin/env bash
# Post-build size pass for the wasm web bundle.
#
# The Kotlin production target wants to download Binaryen itself
# (`kotlinWasmBinaryenSetup`), and this repo forbids project-level repositories
# (`FAIL_ON_PROJECT_REPOS` in settings.gradle.kts) — so CI builds the dist with
# `-x kotlinWasmBinaryenSetup` and runs the optimizer here instead. Optimising a
# build artifact is a build step, not a dependency, so the repository policy stays
# intact.
#
# Measured on this project (2026-11-04):
#   app wasm, development bundle      47.36 MB raw / 10.40 MB gzip
#   app wasm, production + wasm-opt   11.57 MB raw /  3.02 MB gzip
#   skiko  wasm, untouched             8.65 MB raw /  3.33 MB gzip
#
# The skiko payload is already optimal: wasm-opt makes it slightly *larger*
# (8.65 -> 8.71 MB), which is why every file is compared and kept only when the
# optimized version is genuinely smaller.
#
# Usage: scripts/optimize-wasm-size.sh [dist-dir] [--max-gzip-mb N]
set -euo pipefail

DIST="${1:-ui/build/dist/wasmJs/productionExecutable}"
MAX_GZIP_MB=0
if [ "${2:-}" = "--max-gzip-mb" ]; then
    MAX_GZIP_MB="${3:-0}"
fi

if [ ! -d "$DIST" ]; then
    echo "optimize-wasm-size: no dist directory at $DIST" >&2
    exit 1
fi

if ! command -v wasm-opt >/dev/null 2>&1; then
    echo "optimize-wasm-size: wasm-opt not on PATH (install Binaryen >= 116)" >&2
    exit 1
fi
echo "binaryen: $(wasm-opt --version)"

total_gz=0
shopt -s nullglob
for wasm in "$DIST"/*.wasm; do
    name="$(basename "$wasm")"
    raw_before=$(stat -c%s "$wasm")
    gz_before=$(gzip -9c "$wasm" | wc -c)
    # --all-features implies --enable-gc, which is mandatory: Kotlin/Wasm emits
    # GC types and Binaryen refuses the module without it ("invalid type: distinct
    # rec groups would be identical").
    if ! wasm-opt -O3 --all-features --strip-debug "$wasm" -o "$wasm.opt" 2>"$wasm.err"; then
        echo "  $name: wasm-opt failed, keeping original" >&2
        sed 's/^/    /' "$wasm.err" >&2 || true
        rm -f "$wasm.opt" "$wasm.err"
        total_gz=$((total_gz + gz_before))
        continue
    fi
    rm -f "$wasm.err"

    raw_after=$(stat -c%s "$wasm.opt")
    if [ "$raw_after" -lt "$raw_before" ]; then
        mv "$wasm.opt" "$wasm"
        gz_after=$(gzip -9c "$wasm" | wc -c)
        printf '  %-28s %10d -> %-10d raw | %9d -> %-9d gzip\n' \
            "$name" "$raw_before" "$raw_after" "$gz_before" "$gz_after"
        total_gz=$((total_gz + gz_after))
    else
        rm -f "$wasm.opt"
        printf '  %-28s %10d (kept: optimizing made it %d bytes larger)\n' \
            "$name" "$raw_before" "$((raw_after - raw_before))"
        total_gz=$((total_gz + gz_before))
    fi
done

total_mb=$(awk -v b="$total_gz" 'BEGIN { printf "%.2f", b / 1048576 }')
echo "total wasm gzip: ${total_mb} MB"

if [ "$MAX_GZIP_MB" != "0" ]; then
    over=$(awk -v t="$total_mb" -v m="$MAX_GZIP_MB" 'BEGIN { print (t > m) ? 1 : 0 }')
    if [ "$over" = "1" ]; then
        echo "optimize-wasm-size: ${total_mb} MB exceeds the ${MAX_GZIP_MB} MB budget" >&2
        exit 1
    fi
fi
