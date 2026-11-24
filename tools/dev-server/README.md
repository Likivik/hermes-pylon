# dev-server

One server for the built Pylon app. It serves the wasm bundle and forwards the API, auth and the
websocket to the Hermes gateway.

There is deliberately no second implementation of this job. If you need the app to behave like a
real deployment, run this.

## Run

```sh
DIST_ROOT=ui/build/kotlin-webpack/wasmJs/developmentExecutable \
PYLON_PROXY_TARGET=http://127.0.0.1:9119 \
PORT=8080 \
python3 tools/dev-server/serve.py
```

## Configuration

Every setting is an environment variable. There are no command-line flags.

| Variable | Default | Meaning |
| --- | --- | --- |
| `DIST_ROOT` | `/srv` | Directory holding `index.html` and the wasm bundle |
| `PYLON_PROXY_TARGET` | `http://127.0.0.1:9119` | Upstream origin for `/api`, `/auth`, `/ws`, `/webhook`, `/dashboard` |
| `PORT` | `8080` | Listen port |
| `BIND` | `127.0.0.1` | Listen address |
| `PROXY_LOG` | *(unset)* | Path for one JSON line per request |

## The boundary

This server binds **localhost** and is configured **only** by environment variables.

How localhost reaches a phone or another machine — `tailscale serve`, an SSH tunnel, a LAN bind — is
the caller's business, never this file's. Nothing about hostnames, tailnets or ports is hardcoded
here.

**Inside a container, set `BIND=0.0.0.0`.** The container has its own network namespace, so
`127.0.0.1` there is the container's loopback and a published port cannot reach it. Keep the
*host* side locked down instead — `deploy/test/pod.sh` publishes `127.0.0.1:18080:8080`, which is
already host-local.

## Response headers

Every response carries `X-Pylon-Request-Id`. A client that sends one gets it echoed back, so a
browser log and `PROXY_LOG` can be joined on that value.

Cache policy is decided by the filename:

- a content-hashed name (a hex run, e.g. `41203548298c4f07bacb.wasm`) → `public, max-age=31536000, immutable`
- everything else (`ui.js`, `index.html`, `login.html`) → `no-store`

This matters because `ui.js` is **not** hashed while the wasm is. A cached `ui.js` would pair with a
newer wasm and fail at load time.

## Tests

```sh
python3 tools/dev-server/test_serve.py
```

Each test exists because a bug was actually shipped — an origin answering with another app's
document, a missing file falling through to the gateway, and a connection closed mid-sequence. Keep
them.
