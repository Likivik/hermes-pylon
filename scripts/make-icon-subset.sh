#!/usr/bin/env bash
# Regenerate the Material Symbols subset font from the icon set the app uses.
#
# The shipped subset (`composeResources/font/material_symbols_subset.ttf`)
# covers exactly the names in the `HermesIcon` enum — the closed set of icons
# the codebase can draw. When a new icon is added to the enum, re-run this
# script and commit both outputs:
#
#   composeResources/font/material_symbols_subset.ttf
#   composeResources/files/material_symbols_subset.codepoints
#
# Any name in the enum that the full font's codepoints file does not know is
# a hard error (it would render as tofu via the on-demand full-font path).
#
# Usage: scripts/make-icon-subset.sh   (from the repo root)
set -euo pipefail

FONT_IN="ui/src/commonMain/composeResources/font/material_symbols_outlined.ttf"
CODEPOINTS_IN="ui/src/commonMain/composeResources/files/material_symbols.codepoints"
FONT_OUT="ui/src/commonMain/composeResources/font/material_symbols_subset.ttf"
CODEPOINTS_OUT="ui/src/commonMain/composeResources/files/material_symbols_subset.codepoints"
ENUM="ui/src/commonMain/kotlin/com/m57/hermescontrol/ui/icons/HermesIcon.kt"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# Parse `EnumName("symbol_name"),` entries out of the enum.
names="$(grep -oE '^\s+[A-Z][A-Za-z0-9]+\("[a-z_0-9]+"\)' "$ENUM" | grep -oE '"[a-z_0-9]+"' | tr -d '"' | sort -u)"
count="$(printf '%s\n' "$names" | wc -l)"
echo "HermesIcon names: $count"

# Map names -> codepoints; fail on any unknown name (drift between the enum
# and the bundled codepoints file).
missing=0
: > "$TMP/cps.txt"
while IFS= read -r name; do
    line="$(grep -E "^$name " "$CODEPOINTS_IN" || true)"
    if [ -z "$line" ]; then
        echo "ERROR: HermesIcon '$name' not found in $CODEPOINTS_IN" >&2
        missing=1
        continue
    fi
    code="$(printf '%s' "$line" | awk '{print $2}')"
    printf 'U+%s\n' "$code" >> "$TMP/cps.txt"
done <<< "$names"
[ "$missing" -eq 0 ] || exit 1

ranges="$(sort -u "$TMP/cps.txt" | paste -sd, -)"
echo "codepoints: $(wc -l < "$TMP/cps.txt")"

# pyftsubset keeps variable axes (fvar/gvar) by default, so FILL/GRAD keep
# working in the subset. Run through nix for a reproducible fonttools; a
# system-wide pyftsubset is used when nix is unavailable.
if command -v nix >/dev/null 2>&1; then
    timeout 600 nix shell nixpkgs#python3Packages.fonttools -c \
        pyftsubset "$FONT_IN" --unicodes="$ranges" \
        --layout-features='*' --output-file="$TMP/subset.ttf" --no-hinting
else
    pyftsubset "$FONT_IN" --unicodes="$ranges" \
        --layout-features='*' --output-file="$TMP/subset.ttf" --no-hinting
fi

# Emit the subset codepoints file (name + hex, same format as the full one).
while IFS= read -r name; do
    grep -E "^$name " "$CODEPOINTS_IN"
done <<< "$names" > "$TMP/codepoints.txt"

before="$(stat -c%s "$FONT_OUT" 2>/dev/null || echo 0)"
cp "$TMP/subset.ttf" "$FONT_OUT"
cp "$TMP/codepoints.txt" "$CODEPOINTS_OUT"
after="$(stat -c%s "$FONT_OUT")"
echo "subset.ttf: $before -> $after bytes"
echo "codepoints: $(wc -l < "$CODEPOINTS_OUT") lines"
echo "OK — commit $FONT_OUT and $CODEPOINTS_OUT together."
