# Capability matrix — what the seeded test instance can actually write

Read from `shared/src/commonMain/kotlin/com/m57/hermescontrol/data/remote/HermesGatewayApi.kt` (1057 lines, the typed gateway surface) plus the Files client. "Write" means a call the **app already makes**, so it is proven to exist rather than assumed.

| Domain | Read | Write | Seedable offline? |
| --- | --- | --- | --- |
| **Files** | `GET api/files`, `api/files/read`, `api/files/download` | `POST api/files/upload`, `api/files/upload-stream`, `api/files/mkdir`; `DELETE api/files` | **Yes** — bytes pushed through the same endpoint the app uses |
| **Keys / env** | `GET api/env` | `PUT api/env`, `DELETE api/env`, `POST api/env/reveal` | **Yes** — fake values, grep guard catches anything key-shaped |
| **Cron** | `GET api/cron/jobs`, `api/cron/jobs/{id}` | `POST api/cron/jobs`, `POST …/pause`, `…/resume`, `…/trigger`, `DELETE api/cron/jobs/{id}` | **Yes** — full CRUD |
| **Webhooks** | `GET api/webhooks` | `POST api/webhooks`, `DELETE`, `PATCH` enable/toggle | **Yes** |
| **Bots / profiles** | `GET api/profiles`, `api/profiles/active`, `api/profiles/{name}/soul` | `POST api/profiles`, `PUT` active / soul / model / description | **Yes** |
| **Skills** | `GET api/skills`, `api/skills/{name}/content` | `PUT api/skills/content`, `POST` toggle | **Yes** — content written locally, no hub call |
| **MCP** | `GET api/mcp/servers`, `api/mcp/catalog` | `POST api/mcp/servers`, `PUT`, `DELETE`, toggle, test | **Yes** — and a deliberately failing server is the state we want |
| **Channels** | `GET api/messaging-platforms` | `POST` configure / test / remove, Telegram onboarding | **Partial** — configure validates a real token; only the *available, not configured* state is trustworthy offline |
| **Sessions** | `GET api/sessions`, `api/sessions/{id}`, `/messages`, `/stats` | rename, delete, prune, bulk-delete | **No** — there is **no create endpoint**. Sessions exist only as a side effect of running a conversation |
| **Logs** | `GET api/logs` | — | **No** — read-only; log lines come from the gateway writing them |
| **Plugins** | `GET api/dashboard/plugins/hub` | install, uninstall, update, enable, disable, providers | **No** — install takes a git URL, which needs egress |

## What the failures mean

Three domains cannot be seeded through the API, and each fails for a different reason:

- **Sessions** have no create call. Renaming and deleting exist; producing one does not.
- **Logs** are read-only by design — the screen shows what the gateway wrote.
- **Plugins** install from a remote git URL.

## The one addition that fixes two of the three

A **stub OpenAI-compatible server** on loopback (`127.0.0.1:19200`) that returns a fixed reply for `/v1/chat/completions`. Point the test profile's model at it and:

- running one conversation **creates a session** through the normal path, so the sessions screen has real rows;
- the gateway **writes log lines** while doing it, so the logs screen has content.

Both are then seeded by using the product, not by reaching around it — which is also the only way those screens get tested at all. Cost: ~40 lines of Python and one config value. It cannot be used for OAuth or model-quality checks, and the plan does not pretend otherwise.

**Plugins** stays unresolved in this pass: installing from a local `git+file:///…` fixture repo may work without egress, but that is unverified and stays a phase-1 probe, not a requirement. Plugins therefore drops to **golden-only** until it is.

## Revised coverage after the matrix

| Class | Domains |
| --- | --- |
| **live-seeded (API)** | Files, Keys, Cron, Webhooks, Bots, Skills, MCP |
| **live-seeded (via stub model)** | Sessions, Logs |
| **partial** | Channels — available/unconfigured states only |
| **golden-only** | Plugins, plus every screen not listed here |
| **manual** | OAuth round-trips, real model calls, provider connect |

Seven API-seeded domains and two product-seeded ones is a working environment. The original plan's "ten domains" was optimistic by three; this table is what the code supports.
