#!/usr/bin/env bash
# Local test runner for hermes-pylon — the same three tiers CI runs, on this box.
#
#   scripts/local-tests.sh sdk            one-time: writable SDK root (GMD fetches its own image)
#   scripts/local-tests.sh unit           1,028 JVM/Robolectric tests        (~2-3 min warm)
#   scripts/local-tests.sh instrumented   89 emulator tests, non-e2e         (~6 min warm)
#   scripts/local-tests.sh e2e            8 E2E classes vs a local gateway   (~2 min warm)
#   scripts/local-tests.sh gateway start|stop|status|health
#
# Verified on Serenity 2026-10-06: unit 1028/1028, instrumented 89/89, e2e 8/8.
#
# PREREQUISITES
#   * `nix develop` works here (this repo's flake: jdk21 + androidenv SDK).
#   * /Storage/Git is mounted EXEC. It is a bind of /panther/Git, and fstab's
#     `user` option on /panther implies noexec — every repo-local binary then
#     fails with "Permission denied" (exit 126), ./gradlew included.
#     Durable fix belongs in the host config; interim on a dev box:
#       sudo mount -o remount,exec,nosuid,nodev /Storage/Git
#   * For `e2e` only: a gateway source checkout (default /Storage/Git/hermes-agent)
#     with a uv venv, installed as CI does:
#       git clone --depth 1 https://github.com/Likivik/hermes-agent.git
#       cd hermes-agent && uv venv .venv --python 3.12
#       uv pip install --python .venv/bin/python -e ".[all]"
#
# WHY e2e NEEDS ITS OWN GATEWAY AND PORT: the harness default is 10.0.2.2:8642,
# and on a dev box 8642-8645 belong to the LIVE gateway. Pointing the E2E classes
# at it would rename/delete/archive real sessions. So this script starts a
# fixture on a free port and passes -P…e2eBaseUrl to redirect the tests.
#
# NOTE ON --rerun: gradle marks a test task UP-TO-DATE when its inputs haven't
# changed, which makes a runner print "BUILD SUCCESSFUL" having executed nothing.
# Every test invocation below passes --rerun so a green run always means the
# tests actually ran.
set -uo pipefail
export PATH=/run/current-system/sw/bin:$PATH

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO"
PORT="${E2E_PORT:-18642}"
GATEWAY_SRC="${GATEWAY_SRC:-/Storage/Git/hermes-agent}"
SDK_ROOT="$REPO/.android-sdk"

E2E_CLASSES="com.m57.hermescontrol.e2e.RenameE2eTest,\
com.m57.hermescontrol.e2e.RenameFailureE2eTest,\
com.m57.hermescontrol.e2e.RailReorderE2eTest,\
com.m57.hermescontrol.e2e.PinE2eTest,\
com.m57.hermescontrol.e2e.DeleteE2eTest,\
com.m57.hermescontrol.e2e.ArchiveE2eTest,\
com.m57.hermescontrol.e2e.SwitchE2eTest,\
com.m57.hermescontrol.e2e.NoAutoCreateE2eTest"

setup_sdk() {
  local nix_sdk
  nix_sdk="$(nix develop -c bash -lc 'echo $ANDROID_SDK_ROOT' 2>/dev/null | tail -1)"
  [ -n "$nix_sdk" ] && [ -d "$nix_sdk" ] || { echo "[sdk] cannot resolve the nix SDK root"; exit 1; }
  mkdir -p "$SDK_ROOT/system-images" "$SDK_ROOT/.temp"
  for d in build-tools cmake cmdline-tools emulator licenses platforms platform-tools tools; do
    [ -e "$nix_sdk/$d" ] && ln -sfn "$nix_sdk/$d" "$SDK_ROOT/$d"
  done
  echo "[sdk] writable root ready at $SDK_ROOT"
  echo "[sdk] system-images stays real+empty: GMD downloads the ATD image itself"
}

# Run gradle inside the devshell with the writable SDK root exported.
# Callers pass the gradle arguments as a single string.
gradle() {
  nix develop -c bash -lc "
    if [ -d '$SDK_ROOT' ]; then
      export ANDROID_SDK_ROOT='$SDK_ROOT'
      export ANDROID_HOME='$SDK_ROOT'
      export PATH=\"\$ANDROID_SDK_ROOT/platform-tools:\$PATH\"
    fi
    ./gradlew $1"
}

# One summary line for ONE tier's XML (mixing tiers is how you get nonsense like
# 'tests=1028' after an emulator run).
# NOTE: stdlib ElementTree is deliberate — the inputs are files this machine's own
# Gradle just wrote under app/build/, not untrusted data, so XXE / billion-laughs
# are out of scope here.
summarise() {
  local pattern
  case "${1:-unit}" in
    unit) pattern='app/build/test-results/testIrisDebugUnitTest/*.xml' ;;
    gmd)  pattern='app/build/outputs/androidTest-results/managedDevice/**/*.xml' ;;
    *)    echo "summarise: unknown tier $1"; return 1 ;;
  esac
  PATTERN="$pattern" python3 - <<'PY'
import glob, os, xml.etree.ElementTree as ET
t = f = e = s = n = 0
for p in glob.glob(os.environ['PATTERN'], recursive=True):
    root = ET.parse(p).getroot()
    # GMD aggregates into <testsuites>, the unit runner writes <testsuite>.
    suites = [root] if root.tag == 'testsuite' else list(root.iter('testsuite'))
    for r in suites:
        n += 1
        t += int(r.get('tests', 0)); f += int(r.get('failures', 0))
        e += int(r.get('errors', 0)); s += int(r.get('skipped', 0))
print(f'[results] xml suites={n} tests={t} failures={f} errors={e} skipped={s}')
PY
}

case "${1:-help}" in
  sdk)          setup_sdk ;;
  unit)         gradle ":app:testIrisDebugUnitTest --console=plain --rerun" ; summarise unit ;;
  instrumented) gradle ":app:e2eApi34IrisDebugAndroidTest --no-daemon --rerun -Dorg.gradle.workers.max=2 \
                  -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect \
                  -Pandroid.testInstrumentationRunnerArguments.notPackage=com.m57.hermescontrol.e2e" ; summarise gmd ;;
  e2e)
    # Always reset: the E2E classes mutate the gateway, so a reused state.db
    # makes the second run fail for reasons unrelated to the code under test.
    python3 scripts/e2e_gateway.py reset "$PORT" \
      || { echo "[e2e] fixture failed to start — aborting (no gateway = confusing failures)"; exit 1; }
    curl -sf -o /dev/null "http://127.0.0.1:$PORT/api/health" \
      || { echo "[e2e] fixture is not healthy on $PORT — aborting"; exit 1; }
    gradle ":app:e2eApi34IrisDebugAndroidTest --no-daemon --rerun -Dorg.gradle.workers.max=2 \
      -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect \
      -Pandroid.testInstrumentationRunnerArguments.class=$E2E_CLASSES \
      -Pandroid.testInstrumentationRunnerArguments.e2ePassword=e2e-pass-123 \
      -Pandroid.testInstrumentationRunnerArguments.e2eBaseUrl=http://10.0.2.2:$PORT/"
    summarise gmd ;;
  gateway)  python3 scripts/e2e_gateway.py "${2:-start}" "$PORT" ;;
  all)      "$0" unit && "$0" instrumented && "$0" e2e ;;
  *)        sed -n '2,30p' "$0" ;;
esac
