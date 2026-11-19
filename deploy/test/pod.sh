#!/usr/bin/env bash
# The throwaway Hermes test instance, as a rootless pod.
#
#   deploy/test/pod.sh up      create the pod, start stub + gateway + dist server
#   deploy/test/pod.sh down    stop and remove everything except the data volume
#   deploy/test/pod.sh status  what is running
#   deploy/test/pod.sh wipe    down, then delete the data volume too
#
# No root, no OS user, no systemd unit: everything runs as the invoking user inside a
# rootless pod on an INTERNAL podman network. Verified on this host (podman 6.1.4,
# netavark + pasta): a published port on an internal network answers on the host
# (curl -> 200) while egress from the container is blocked.
#
# Pylon and Chrome stay on the host. Chrome in the pod would need a much larger image,
# and an internal network plus one loopback port already gives what we want.
set -euo pipefail

POD="${PYLON_TEST_POD:-pylon-test}"
NET="${PYLON_TEST_NET:-pylon-test-net}"
VOL="${PYLON_TEST_VOL:-pylon-test-data}"
IMAGE="${PYLON_TEST_IMAGE:-docker.io/nousresearch/hermes-agent:latest}"
GATEWAY_PORT="${PYLON_TEST_GATEWAY_PORT:-19119}"
PREVIEW_PORT="${PYLON_TEST_PREVIEW_PORT:-18080}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/../.." && pwd)"
DIST="${PYLON_TEST_DIST:-$REPO/ui/build/dist/wasmJs/productionExecutable}"

say() { printf '%s\n' "$*"; }

preflight() {
    command -v podman >/dev/null || { say "podman is required"; exit 1; }
    podman info >/dev/null 2>&1 || { say "podman does not work rootless here"; exit 1; }
    case "$IMAGE" in
        *:latest)
            if [ "${PYLON_ALLOW_LATEST:-0}" != "1" ]; then
                say "refusing to run :latest — set PYLON_TEST_IMAGE to a pinned tag or digest,"
                say "or PYLON_ALLOW_LATEST=1 if you really want a moving target"
                exit 1
            fi
            ;;
    esac
    [ -d "$DIST" ] || { say "no dist at $DIST — build it first (:ui:wasmJsBrowserDistribution)"; exit 1; }
}

network() {
    podman network exists "$NET" 2>/dev/null || podman network create --internal "$NET" >/dev/null
}

wait_for() {
    local url="$1" label="$2"
    for _ in $(seq 1 45); do
        if curl -fsS -m 2 "$url" >/dev/null 2>&1; then say "$label up: $url"; return 0; fi
        sleep 2
    done
    say "$label did not answer at $url" >&2
    podman pod logs "$POD" 2>&1 | tail -20 >&2
    return 1
}

up() {
    preflight
    network
    if ! podman pod exists "$POD" 2>/dev/null; then
        podman pod create --name "$POD" --network "$NET" \
            -p "127.0.0.1:${GATEWAY_PORT}:9119" \
            -p "127.0.0.1:${PREVIEW_PORT}:8080" >/dev/null
    fi

    # Stub first: sessions and logs are seeded by running one conversation against it.
    podman run -d --pod "$POD" --name "${POD}-stub" \
        -v "$HERE/stub_provider.py:/opt/stub_provider.py:ro,Z" \
        "$IMAGE" python3 /opt/stub_provider.py --host 127.0.0.1 --port 19200 >/dev/null

    podman run -d --pod "$POD" --name "${POD}-gateway" \
        -e HERMES_HOME=/opt/data \
        -e API_SERVER_HOST=0.0.0.0 \
        -e API_SERVER_KEY="${API_SERVER_KEY:-stub-key-0123456789abcdef0123456789}" \
        -v "${VOL}:/opt/data:Z" \
        "$IMAGE" gateway run >/dev/null

    # The built app, served over the pod's shared loopback and published to the host.
    # It must also forward /api, /auth and the websocket to the dashboard: a bare static
    # server leaves the app dialling its own origin and failing, so nothing can assert a
    # connected socket. See dist_proxy.py.
    podman run -d --pod "$POD" --name "${POD}-dist" \
        -e DIST_ROOT=/srv \
        -e PYLON_PROXY_TARGET=http://127.0.0.1:9119 \
        -v "$DIST:/srv:ro,Z" \
        -v "$HERE/dist_proxy.py:/opt/dist_proxy.py:ro,Z" \
        "$IMAGE" python3 /opt/dist_proxy.py >/dev/null

    wait_for "http://127.0.0.1:${PREVIEW_PORT}/" "dist server"
    # 19119 belongs to the dashboard, which starts after auth is configured, so wait here
    # only on the gateway container being alive — otherwise up() waits on itself.
    for _ in $(seq 1 20); do
        if podman ps --filter "name=${POD}-gateway" --format '{{.Status}}' | grep -q "^Up"; then
            say "gateway container up"
            break
        fi
        sleep 2
    done
}

# Discovered live: the dashboard refuses a non-loopback bind until an auth provider
# is registered, so the fixture password goes in BEFORE the dashboard starts.
dashboard_up() {
    podman run -d --pod "$POD" --name "${POD}-dashboard" \
        -e HERMES_HOME=/opt/data \
        -v "${VOL}:/opt/data:Z" \
        "$IMAGE" dashboard --host 0.0.0.0 --port 9119 --no-open --skip-build >/dev/null
    wait_for "http://127.0.0.1:${GATEWAY_PORT}/" "dashboard"
}

configure() {
    # Every setting goes through `hermes config set` inside the container: the guard refuses
    # hand-written config, and a guessed schema could route to a real endpoint.
    cfg() { podman exec "${POD}-gateway" hermes config set "$@"; }
    cfg providers.stub \
        "{\"name\":\"Stub\",\"api\":\"http://127.0.0.1:19200/v1\",\"key_env\":\"PYLON_STUB_KEY\",\"api_mode\":\"chat_completions\",\"models\":[\"stub-1\"]}"
    cfg model.provider custom:stub
    cfg model.default stub-1
    # Live finding: the dashboard refuses a 0.0.0.0 bind with no auth provider registered.
    # The fixture password hashes via the gateway's own helper, so nothing is guessed.
    local HASH
    HASH=$(podman exec "${POD}-gateway" python3 -c "from plugins.dashboard_auth.basic import hash_password; print(hash_password('test-password'))" 2>/dev/null)
    [ -n "$HASH" ] || { say "could not hash the fixture password"; return 1; }
    cfg dashboard.basic_auth.username seed
    cfg "dashboard.basic_auth.password_hash" "$HASH"
    printf 'PYLON_STUB_KEY=stub-key\n' | podman exec -i "${POD}-gateway" sh -c 'cat > /opt/data/.env'

    local seen
    seen=$(podman exec "${POD}-gateway" hermes config get model.provider 2>/dev/null | tr -d '" ')
    [ "$seen" = "custom:stub" ] || { say "model.provider is '$seen', expected custom:stub" >&2; return 1; }
    say "model routed to the stub"
}

provider_up() {
    local src="$REPO/deploy/test/fake_llm_provider.py"
    local scripts="$REPO/deploy/test/chat_scripts.py"
    [ -f "$src" ] || { say "missing vendored provider: $src" >&2; return 1; }
    [ -f "$scripts" ] || { say "missing chat scripts: $scripts" >&2; return 1; }
    # The stub answers one canned line and no tool calls. The scripted provider replaces it on
    # the same port, so the gateway config (`custom:stub` -> 127.0.0.1:19200) never changes.
    podman stop "${POD}-stub" >/dev/null 2>&1 || true
    podman rm -f "${POD}-stub" >/dev/null 2>&1 || true
    podman rm -f "${POD}-provider" >/dev/null 2>&1 || true
    podman run -d --pod "$POD" --name "${POD}-provider" \
        -v "$src:/opt/fake_llm_provider.py:ro" \
        -v "$scripts:/opt/chat_scripts.py:ro" \
        "$IMAGE" python3 /opt/chat_scripts.py markdown 19200 >/dev/null
    local code=""
    for _ in $(seq 1 20); do
        code=$(podman exec "${POD}-gateway" sh -c \
            'curl -s -m 2 -o /dev/null -w "%{http_code}" -X POST http://127.0.0.1:19200/v1/chat/completions -H "content-type: application/json" -d "{\"model\":\"stub\",\"messages\":[{\"role\":\"user\",\"content\":\"ping\"}]}"' 2>/dev/null) || true
        [ "$code" = "200" ] && { say "scripted provider up on :19200 (script: markdown)"; return 0; }
        sleep 0.5
    done
    say "scripted provider did not answer on :19200 (last: ${code:-none})" >&2
    podman logs --tail 20 "${POD}-provider" 2>&1 | tail -5 >&2 || true
    return 1
}

down() {
    podman pod stop "$POD" >/dev/null 2>&1 || true
    podman pod rm -f "$POD" >/dev/null 2>&1 || true
    say "pod removed (volume $VOL kept)"
}

status() {
    podman ps --pod --format '{{.Names}}\t{{.Status}}' 2>/dev/null | grep "^${POD}-" || say "nothing running"
}

case "${1:-}" in
    up) up; configure; provider_up ; dashboard_up ;;
    provider) provider_up ;;
    down) down ;;
    wipe) down; podman volume rm -f "$VOL" >/dev/null 2>&1 || true; say "volume $VOL deleted" ;;
    status) status ;;
    *) say "usage: $0 {up|down|wipe|status}"; exit 2 ;;
esac
