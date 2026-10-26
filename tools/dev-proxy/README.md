# Dev proxy for the wasm bundle

The wasm target treats the page origin as the gateway, so serving the bundle and
proxying `/api/*` plus the WebSocket from the same origin is the only way to run
it against a real gateway. This script does that and writes an access log.

```bash
# build once
/var/lib/hermes/bin/pylon-build.sh :ui:wasmJsBrowserDevelopmentWebpack

# serve + log
python3 tools/dev-proxy/serve_pylon_web.py \
  --root ui/build/kotlin-webpack/wasmJs/developmentExecutable \
  --resources ui/build/processedResources/wasmJs/main \
  --gateway 127.0.0.1:8787 \
  --port 8080 \
  --log /var/log/pylon-proxy.jsonl
```

## Why the log matters

Every line carries the request id the client sent:

```json
{"ts": 1770000000.123, "kind": "proxy", "method": "GET", "path": "/api/status", "ms": 18, "req": "px-3f9a1c2b-1770000000123"}
```

The client shows the same `req` in Diagnostics (`GET /api/status ... req=px-…`),
so a report of "it timed out" becomes:

```bash
grep 'px-3f9a1c2b' /var/log/pylon-proxy.jsonl
```

which distinguishes three very different failures that otherwise look identical
from a phone: the request never arrived, it arrived and the gateway never
answered, or it answered and the *client* failed to read it.

## Notes

- Routing rule: anything that exists as a file is served; everything else is
  proxied. Prefix-based routing broke the login flow (`/dashboard/login` →
  `302` → `/login`, which is a gateway route, not a file).
- `.wasm` is served as `application/wasm`: mobile browsers refuse a wrong type
  for `WebAssembly.instantiateStreaming`.
- The WebSocket upgrade is proxied by piping both directions after the request
  line is forwarded; no framing is inspected.
- This is a development tool. It has no authentication of its own — it inherits
  whatever the gateway does, and it must not be exposed beyond a trusted network.
