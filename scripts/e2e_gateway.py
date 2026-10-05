#!/usr/bin/env python3
"""Local E2E gateway fixture for hermes-pylon.

Mirrors .github/workflows/e2e.yml's "Start real Hermes gateway with ephemeral
auth" + "Seed gateway profile sessions" steps, but on a LOCAL port.

Why a fixture and not the host's gateway:
  * CI owns 8642; a dev box usually has the LIVE Hermes gateway on 8642-8645.
    Pointing the E2E classes at that would rename/delete/archive REAL sessions.
  * The packaged `hermes` (e.g. hermes-agent-0.21.1 on NixOS) ships no
    `plugins.dashboard_auth`, so /auth/password-login can never succeed against
    it. The gateway must be the source checkout CI uses.

Environment overrides:
  E2E_HERMES_HOME   (default /tmp/e2e-home)      own config.yaml + state.db
  E2E_HERMES_BIN    (default <gateway-src>/.venv/bin/hermes)
  E2E_VENV_PYTHON   (default <gateway-src>/.venv/bin/python) used for hashing
  E2E_GATEWAY_SRC   (default /Storage/Git/hermes-agent)

Usage:
  e2e_gateway.py setup  [PORT]   # write config.yaml + seed state.db, then exit
  e2e_gateway.py start  [PORT]   # setup + start the gateway, wait for health
  e2e_gateway.py health [PORT]
  e2e_gateway.py stop   [PORT]
"""
import os
import secrets
import signal
import sqlite3
import subprocess
import sys
import time
from pathlib import Path

HOME = Path(os.environ.get("E2E_HERMES_HOME", "/tmp/e2e-home"))
GATEWAY_SRC = os.environ.get("E2E_GATEWAY_SRC", "/Storage/Git/hermes-agent")
HERMES_BIN = os.environ.get("E2E_HERMES_BIN", f"{GATEWAY_SRC}/.venv/bin/hermes")
VENV_PYTHON = os.environ.get("E2E_VENV_PYTHON", f"{GATEWAY_SRC}/.venv/bin/python")
PASSWORD = "e2e-pass-123"
USERNAME = "admin"

HASH_SNIPPET = (
    "from plugins.dashboard_auth.basic import hash_password;"
    f"print(hash_password({PASSWORD!r}))"
)


def real_password_hash() -> str:
    """Hash via the gateway's own implementation — its format is `scrypt$...`.

    A hand-rolled `hashlib.scrypt(..., salt=b'hermes-e2e').hex()` produces a
    bare 64-char digest and every login returns 401: same password, wrong
    serialisation. Never re-implement this locally.
    """
    try:
        out = subprocess.run(
            [VENV_PYTHON, "-c", HASH_SNIPPET],
            capture_output=True,
            text=True,
            check=True,
        )
        digest = out.stdout.strip()
    except (OSError, subprocess.CalledProcessError) as exc:
        raise SystemExit(
            f"[config] cannot hash via {VENV_PYTHON}: {exc}\n"
            f"[config] install the gateway source first:\n"
            f"         cd {GATEWAY_SRC} && uv venv .venv --python 3.12 && "
            f'uv pip install --python .venv/bin/python -e ".[all]"'
        )
    if not digest.startswith("scrypt$"):
        raise SystemExit(f"[config] unexpected digest format: {digest[:16]}")
    return digest


def write_config() -> None:
    HOME.mkdir(parents=True, exist_ok=True)
    cfg_path = HOME / "config.yaml"
    cfg_path.write_text(
        "dashboard:\n"
        "  basic_auth:\n"
        f"    username: {USERNAME}\n"
        f'    password_hash: "{real_password_hash()}"\n'
        '    password: ""\n'
        f"    secret: {secrets.token_urlsafe(32)}\n"
        # `basic` is a bundled-but-OPT-IN plugin: `_BUNDLED_DEFAULT_ON_KINDS` is
        # only {backend, platform, model-provider}, and a dashboard-auth plugin
        # is neither. Without this entry `list_providers()` stays empty, the
        # auth gate sees no provider, and `hermes serve --host 0.0.0.0` fails
        # closed ("Refusing to bind dashboard to 0.0.0.0").
        "plugins:\n"
        "  enabled:\n"
        "    # The canonical key, exactly as `hermes plugins enable dashboard_auth/basic`\n"
        "    # writes it. Bare 'basic' is NOT sufficient — with it the plugin still\n"
        "    # reports 'not enabled' and the auth gate fails closed.\n"
        "    - dashboard_auth/basic\n"
        "  disabled: []\n"
    )
    print(f"[config] wrote {cfg_path} (scrypt digest via {VENV_PYTHON})")
    enable_basic_plugin()


def enable_basic_plugin() -> None:
    """Enable the bundled dashboard-auth provider via the CLI.

    Hand-writing `plugins.enabled` is a trap: the loader wants both the
    `basic` and `dashboard_auth/basic` keys (and normalises `_config_version`)
    — writing only one leaves `list_providers()` empty and `hermes serve
    --host 0.0.0.0` fails closed. Let the CLI own the shape.
    """
    env = dict(os.environ, HERMES_HOME=str(HOME))
    try:
        out = subprocess.run(
            [HERMES_BIN, "plugins", "enable", "basic"],
            capture_output=True,
            text=True,
            env=env,
            cwd=GATEWAY_SRC,
            timeout=120,
        )
        msg = (out.stdout + out.stderr).strip().splitlines()
        print(f"[config] plugins enable basic -> {msg[-1] if msg else 'no output'}")
    except (OSError, subprocess.SubprocessError) as exc:
        raise SystemExit(f"[config] `hermes plugins enable basic` failed: {exc}")


def seed_db() -> None:
    """Insert the rows the E2E classes long-press against (mirrors CI)."""
    db = HOME / "state.db"
    now = time.time()
    con = sqlite3.connect(db)
    # NEVER create the table ourselves: the gateway's own schema has ~30 columns
    # (user_id, chat_id, thread_id, origin_json, token counters, cwd, ...) and a
    # hand-rolled subset silently wins the CREATE TABLE IF NOT EXISTS race,
    # breaking every session query. The gateway must have booted first.
    cols = [r[1] for r in con.execute("PRAGMA table_info(sessions)")]
    if "user_id" not in cols:
        raise SystemExit(
            f"[seed] refusing to seed {db}: `sessions` is missing the gateway's "
            "schema (no user_id column). Start the gateway first — it creates "
            "the table — then seed. (Seed order is the whole reason CI seeds "
            "after its health check.)"
        )
    rows = [
        ("stored-current", "Current chat", 5, now - 7200),
        ("stored-bg", "Background chat", 3, now - 3600),
        ("reaped-bg", "Reaped chat", 99999, now - 86400),
        ("item-top", "Top chat", 5, now - 1800),
        ("item-low", "Low chat", 3, now - 5400),
        ("stale-bg", "Stale chat", 2, now - 7 * 86400),
    ]
    con.executemany(
        "INSERT OR REPLACE INTO sessions(id, source, title, message_count, started_at, ended_at, end_reason) "
        "VALUES (?, 'telegram', ?, ?, ?, NULL, NULL)",
        rows,
    )
    con.execute(
        "INSERT OR REPLACE INTO sessions(id, source, title, message_count, started_at, ended_at, end_reason) "
        "VALUES ('stale-bg', 'telegram', 'Stale chat', 2, ?, ?, 'ended')",
        (now - 7 * 86400, now - 86400),
    )
    con.commit()
    con.close()
    print(f"[seed] {len(rows)} sessions -> {db}")


def health(port: str) -> bool:
    import urllib.error
    import urllib.request

    try:
        with urllib.request.urlopen(
            f"http://127.0.0.1:{port}/api/health", timeout=5
        ) as r:
            return r.status == 200
    except (urllib.error.URLError, OSError):
        return False


def require_free_port(port: str) -> None:
    """Refuse to start on an occupied port.

    A fixture that silently fails to bind leaves the tests talking to the LIVE
    gateway on 8642+, which would mutate real sessions. Fail loudly instead.
    """
    import socket

    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            s.bind(("0.0.0.0", int(port)))
        except OSError as exc:
            print(
                f"[preflight] port {port} is ALREADY IN USE ({exc}). "
                f"Pick another (e.g. {int(port) + 1}). CI owns 8642; a dev box usually has "
                "the live gateway on 8642-8645.",
                file=sys.stderr,
            )
            sys.exit(3)


def start(port: str) -> None:
    require_free_port(port)
    if not Path(HERMES_BIN).exists():
        raise SystemExit(f"[start] no gateway binary at {HERMES_BIN}")
    log = Path(f"/tmp/e2e-gateway-{port}.log")
    pid_file = Path(f"/tmp/e2e-gateway-{port}.pid")
    env = dict(
        os.environ,
        HERMES_HOME=str(HOME),
        # The host shell exports HERMES_BUNDLED_PLUGINS pointing at the Nix-store
        # packaged hermes-agent-0.21.1 plugins/ dir. get_bundled_plugins_dir()
        # honours that env var first, so without overriding it the loader
        # imports the Nix-store plugins against the editable-installed
        # hermes_cli (different versions). basic/__init__.py in that copy does
        # `from plugins.dashboard_auth._shared import (...)` and the _shared
        # module that ships there pulls in symbols the editable hermes_cli
        # doesn't expose (e.g. classify_jwks_lookup_error) → ModuleNotFoundError
        # for every dashboard_auth provider → list_providers() stays empty →
        # the auth gate fails closed. Force the loader at the gateway checkout's
        # own plugins/ dir, where basic/__init__.py is self-contained.
        HERMES_BUNDLED_PLUGINS=f"{GATEWAY_SRC}/plugins",
        # plugins/dashboard_auth/basic/plugin.yaml declares
        #   requires_env: [HERMES_DASHBOARD_BASIC_AUTH_USERNAME]
        # Without it the bundled basic provider never registers, the auth gate
        # sees zero providers, and a non-loopback bind fails closed — no matter
        # what config.yaml says. (Env wins over config.yaml when non-empty.)
        HERMES_DASHBOARD_BASIC_AUTH_USERNAME=USERNAME,
        HERMES_DASHBOARD_BASIC_AUTH_PASSWORD_HASH=real_password_hash(),
    )
    with log.open("wb") as fh:
        proc = subprocess.Popen(
            [HERMES_BIN, "serve", "--skip-build", "--host", "0.0.0.0", "--port", port],
            stdout=fh,
            stderr=subprocess.STDOUT,
            env=env,
            # Run from the gateway checkout: the bundled dashboard-auth plugin
            # (`plugins.dashboard_auth.basic`) is discovered relative to the
            # working tree, and CI also starts the gateway from that directory.
            # Launching from another cwd leaves `list_providers()` empty and the
            # gateway fails closed on a non-loopback bind.
            cwd=GATEWAY_SRC,
            start_new_session=True,
        )
    pid_file.write_text(str(proc.pid))
    print(f"[start] pid={proc.pid} port={port} bin={HERMES_BIN} log={log}")
    for i in range(1, 61):
        if health(port):
            print(f"[start] gateway up after {i}s -> http://127.0.0.1:{port}")
            # Seed only AFTER the gateway has created its real schema. Seeding
            # first creates a 13-column `sessions` table, which makes the
            # gateway's own `CREATE TABLE IF NOT EXISTS sessions (...30 cols...)`
            # a no-op -- every session query then 500s on missing columns and
            # the rail renders empty. CI seeds after the health gate, so do we.
            seed_db()
            print(
                "[start] run the E2E classes with:\n"
                f"  ./gradlew e2eApi34IrisDebugAndroidTest --no-daemon \\\n"
                f"    -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect \\\n"
                f"    -Pandroid.testInstrumentationRunnerArguments.class=<fqns> \\\n"
                f"    -Pandroid.testInstrumentationRunnerArguments.e2ePassword={PASSWORD} \\\n"
                f"    -Pandroid.testInstrumentationRunnerArguments.e2eBaseUrl=http://10.0.2.2:{port}/"
            )
            return
        if proc.poll() is not None:
            print(f"[start] process exited rc={proc.returncode}; log tail:")
            print(log.read_text()[-2000:])
            sys.exit(1)
        time.sleep(1)
    print("[start] gateway never became healthy; log tail:")
    print(log.read_text()[-2000:])
    sys.exit(1)


def wait_port_free(port: str, timeout: float = 20.0) -> None:
    """Block until nothing is listening on `port`.

    SIGTERM is asynchronous: without this wait, `reset` starts a new gateway
    while the previous one still owns the socket, the preflight refuses, and the
    E2E run proceeds against no gateway at all (observed: run 1 failed in 58s,
    run 2 passed).
    """
    import socket

    deadline = time.time() + timeout
    while time.time() < deadline:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            try:
                s.bind(("0.0.0.0", int(port)))
                return
            except OSError:
                time.sleep(0.25)
    raise SystemExit(
        f"[stop] port {port} still busy after {timeout:.0f}s — refusing to "
        "start a second gateway (that is how tests end up talking to the wrong "
        "one)."
    )


def stop(port: str) -> None:
    pid_file = Path(f"/tmp/e2e-gateway-{port}.pid")
    if not pid_file.exists():
        print("[stop] no pid file")
        wait_port_free(port)
        return
    pid = int(pid_file.read_text().strip())
    # `hermes serve` spawns a child that holds the listening socket, so killing
    # the recorded pid alone leaves the port occupied. Popen used
    # start_new_session=True, so pgid == pid: signal the whole group.
    for sig, label, pause in (
        (signal.SIGTERM, "SIGTERM", 2.0),
        (signal.SIGKILL, "SIGKILL", 1.0),
    ):
        try:
            os.killpg(pid, sig)
            print(f"[stop] {label} -> process group {pid}")
        except ProcessLookupError:
            print(f"[stop] group {pid} already gone")
            break
        time.sleep(pause)
        try:
            os.killpg(pid, 0)
        except ProcessLookupError:
            break
    pid_file.unlink(missing_ok=True)
    wait_port_free(port)


def reset(port: str) -> None:
    """Clean state, then start. This is what CI gets for free on a fresh runner.

    The E2E classes MUTATE the gateway (rename / delete / archive / pin), so a
    second run against a reused state.db begins with a half-deleted profile and
    fails for reasons unrelated to the code under test -- observed for real:
    run 1 passed 8/8, run 2 failed RailReorder + Pin because run 1 had deleted
    stored-current / stored-bg / item-low and renamed another row.
    Stop, drop the DB, start (the gateway recreates its 56-column schema), seed.
    """
    stop(port)
    for suffix in ("", "-wal", "-shm"):
        (HOME / f"state.db{suffix}").unlink(missing_ok=True)
    print("[reset] state.db dropped; restarting for a clean profile")
    start(port)


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else "setup"
    port = sys.argv[2] if len(sys.argv) > 2 else os.environ.get("E2E_PORT", "18642")
    if cmd in ("setup", "start"):
        write_config()
    if cmd == "setup":
        print(f"[setup] ready for port {port}")
    elif cmd == "start":
        start(port)
    elif cmd == "reset":
        reset(port)
    elif cmd == "health":
        print("[health]", "up" if health(port) else "DOWN")
    elif cmd == "stop":
        stop(port)
    else:
        print(__doc__)
        sys.exit(2)
