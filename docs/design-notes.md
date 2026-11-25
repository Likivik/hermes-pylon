# Design notes

Things that are not decided by code, recorded so they are not re-invented (or
lost when a temporary screen is replaced).

## Boot log aesthetic (retired 2026-11-03, worth reusing)

The first web shell drew its boot state as a terminal in the corner of a dark
page: green monospace lines, each event appended on its own line, no spinner.

```
booting…
module script tag executed (module graph may still be loading)
canvas 390x844 css=390pxx844px
```

It read as "a machine waking up" rather than "an app pretending to be nothing
while it loads", which is why it was liked. The exact styling, should it come
back for a loading or diagnostics screen:

```css
background: #101418;              /* page */
color: #8fd6a0;                   /* boot text, soft terminal green */
font: 12px/1.5 monospace;
padding: 8px;
white-space: pre-wrap;
```

Notes on reuse:

- It was honest but developer-facing: stage names like "module graph" are not
  user vocabulary. Reuse the *look*, replace the *content*.
- Its real value was the failure case, not the happy path — a dead page with a
  readable reason instead of a black rectangle. That part survives in
  `ui/src/wasmJsMain/resources/index.html`.
- Served shell: `http://127.0.0.1:8080/` (tailnet `:8445`), `/api/*` proxied to
  the dashboard so the browser stays same-origin.

## Login routing (decided 2026-11-03)

The web build keeps the landing screen in front of the login form. Auto-routing
an unauthenticated browser straight to `AuthLoginScreen` was considered and
**declined** — one tap is not worth diverging from the Android flow. Revisit only
if the landing page grows real content for web.

## Icon font: the decision is written, the price was not (2026-11-04)

`MaterialSymbols.kt` documents *why* the rail renders icons from a variable font
rather than `ImageVector`s: `material-icons-extended` is Android-only and pinned
at 1.7.3 since the K2 move, so it is unavailable to wasmJs/JVM; Material Symbols
is the maintained successor and CMP supports variable fonts on every target since
1.8.0. It also explains TTF over WOFF2 (WOFF2 is web-only, and this code is
`commonMain`).

What that note never recorded is what the choice costs on the web target.
Measured 2026-11-04 against the production bundle:

| resource | raw | gzip | brotli |
| --- | ---: | ---: | ---: |
| `font/material_symbols_outlined.ttf` | 10.73 MB | 4.86 MB | **4.06 MB** |
| `values/strings.commonMain.cvr` | 55 KB | 17 KB | 15 KB |
| `files/material_symbols.codepoints` | 79 KB | 30 KB | 26 KB |
| app wasm (post-`wasm-opt`) | 11.57 MB | 3.02 MB | 2.10 MB |

The icon font outweighs the application code on the wire, and the app renders
roughly 30 distinct glyphs while the font carries 4,301. On a phone this is the
largest single asset after the wasm.

### Open question: subset, or drop the font

Two options, not yet decided:

1. **Subset at build time** (`pyftsubset` against the codepoints actually used).
   Expected to reach tens of KB. Keeps one code path for all targets. Cost: a
   build step that can silently omit a glyph if the icon set changes — needs a
   test or a coverage check against `material_symbols.codepoints`.

   The subset is a *fast path*, not a restriction: the rail may use any of the
   4,301 symbols, so the full font stays available and is loaded on demand for
   anything outside the subset.

   **Decided 2026-11-04, do not re-raise:** do not limit the picker to a curated
   palette in order to avoid the second font. Choosing an icon is a real feature
   of the rail, and "pick from 150" is a different product. The 4 MB is the price
   of the feature, paid only by users who leave the defaults.
2. **`ImageVector`s instead of a font**, following `HermesIcons.Bolt`, which
   already vendors its glyph in `commonMain`. Removes the fetch entirely for the
   fixed icon set. Note this cannot replace the font for user-chosen rail icons:
   `ImageVector`s are compiled in, so an arbitrary glyph would need code. Viable
   only for the constant icons.

Related: the picker surface (`?icons`) renders the whole set, which is why the
full font is currently shipped. If the font is subset or dropped, that surface
stops being representative and needs its own answer (lazy-load the full font on
that route, or read glyph names from `material_symbols.codepoints` as text).

### Loading order (separate from size)

Compose web resources load via Fetch *after* the runtime starts, so
`strings.commonMain.cvr` lands late and every `stringResource` waits on it — that
is the blank-label frame, not the font. Fixed shapes: `<link rel="preload">` for
the fixed-path resources, and in Kotlin `preloadFont()` / `FontFamily.Resolver.preload()`
(CMP 1.8+) before the shell is told the UI is ready.
