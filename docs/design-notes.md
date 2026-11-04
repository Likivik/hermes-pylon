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
