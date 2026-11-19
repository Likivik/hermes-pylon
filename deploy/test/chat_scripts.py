#!/usr/bin/env python3
"""Named chat scripts for the Pylon UI probes.

The engine is upstream: `deploy/test/fake_llm_provider.py` is vendored byte-identical from
hermes-agent and speaks the OpenAI wire format. This file is *our* layer — the content Pylon
has to render, addressed by name so a probe never has to edit Python to ask for a different
message shape.

    python3 chat_scripts.py markdown        # serve the `markdown` script on :19200
    python3 chat_scripts.py --list          # what is available

The gateway reaches the provider at 127.0.0.1 inside the pod, so this runs there, delivered by
`pod.sh provider`.
"""

from __future__ import annotations

import sys
from http.server import ThreadingHTTPServer

import fake_llm_provider as fake

LONG_TOOL_OUTPUT = "\n".join(f"line {i:04d}: package build artifact {i:04d} checksum ok" for i in range(2000))

MARKDOWN = """## Deployment notes

The gateway is live at `http://127.0.0.1:8080` and reachable over the tailnet.

| step | command | expected |
| --- | --- | --- |
| build | `./gradlew :ui:wasmJsBrowserProductionWebpack` | exit 0 |
| serve | `python3 pylon-preview-gzip.py` | listens on 8080 |

A fenced block with a long line, to check wrapping and horizontal scroll:

```kotlin
private fun resolveFence(row: SessionRow): SessionFence? = row.runtimeRevision?.let { SessionFence(it, row.runtimeGeneration) }
```

- a bullet
- a second bullet with **bold**, *italic* and `inline code`
- a [link](https://example.test/docs)

> A quote, to check the left rule and the muted colour.
"""

SCRIPTS = {
    # Structure and typography: headings, table, long fenced line, list, quote, link.
    "markdown": [fake.Text(MARKDOWN)],
    # Rendering of a very large tool result: collapse, scroll, copy.
    "long-tool": [
        fake.ToolCall("terminal", {"command": "printf 'x\\n' | head -2000"}),
        fake.Text("Done. The output above is 2,000 lines."),
    ],
    # A tool that fails: error styling, no false success.
    "failing-tool": [
        fake.ToolCall("terminal", {"command": "exit 1"}),
        fake.Text("The command failed. Here is what I would try instead."),
    ],
    # An image reference inside markdown: a broken link must degrade to text, not a torn glyph.
    "image": [fake.Text("Screenshot: ![dashboard](https://example.test/missing.png)\n\nThat image does not exist.")],
    # A long answer, to check scroll-follow while text streams in.
    "long-stream": [fake.Text("\n\n".join(f"Paragraph {i}: " + "streaming text " * 40 for i in range(12)))],
}


def main(argv: list[str]) -> int:
    if not argv or argv[0] == "--list":
        print("\n".join(sorted(SCRIPTS)))
        return 0
    name = argv[0]
    if name not in SCRIPTS:
        print(f"unknown script: {name}; known: {', '.join(sorted(SCRIPTS))}", file=sys.stderr)
        return 2
    port = int(argv[1]) if len(argv) > 1 else 19200
    # Upstream's own bootstrap: a fixed port needs the handler built around the instance,
    # because `start()` deliberately binds port 0.
    server = fake.FakeLLMServer(SCRIPTS[name])
    server._server = ThreadingHTTPServer(("127.0.0.1", port), fake._handler_for(server))
    print(f"serving '{name}' on 127.0.0.1:{port}", flush=True)
    server._server.serve_forever()
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
