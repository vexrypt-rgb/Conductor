#!/usr/bin/env bash
# Live harness run: local vanilla server + Ostinato dev client (Xvfb) + Conductor harness mod.
#
#   scripts/live-run.sh <workdir>
#
# <workdir> must contain server/server.jar (vanilla, same Minecraft version as MC) with eula.txt and
# server.properties.
# Env: MC (Minecraft version of the harness, default 1.21.11; see mcTargets in build.gradle),
#      OSTINATO_DIR (the Ostinato checkout for that version, default ../Ostinato),
#      GRADLE_ARGS (extra args for both Gradle builds, e.g. an init script or -PostinatoDir_1_21_4=...),
#      CLIENT_TIMEOUT (seconds, default 1200),
#      HARNESS_SCENARIO (stage4, evidence, or connect-only: join and wait while installed modules run),
#      EXTRA_MODS (colon-separated jars to load too, e.g. the Conductor mod and module jars),
#      HARNESS_HOLD (seconds connect-only waits, default 60).
# Output: <workdir>/harness.jsonl (per-tick evidence), server.log, client.log.
set -euo pipefail

WORK=$(realpath "$1")
ROOT=$(cd "$(dirname "$0")/.." && pwd)
OSTINATO_DIR=$(realpath "${OSTINATO_DIR:-$ROOT/../Ostinato}")
CLIENT_TIMEOUT=${CLIENT_TIMEOUT:-1200}
MC=${MC:-1.21.11}
OUT="$WORK/harness.jsonl"

(cd "$ROOT" && ./gradlew ${GRADLE_ARGS:-} ":live-harness-$MC:build" --no-daemon -q)
JAR=$(ls "$ROOT/live-harness/$MC/build/libs/"live-harness-*.jar | grep -v -- '-dev.jar' | head -1)

rm -f "$OUT" "$WORK/server.log" "$WORK/client.log"
rm -rf "$WORK/server/world"
mkfifo "$WORK/server.in" 2>/dev/null || true
(cd "$WORK/server" && tail -f "$WORK/server.in" | java -Xmx2G -jar server.jar nogui > "$WORK/server.log" 2>&1) &
SERVER_PID=$!
cleanup() {
    echo "stop" > "$WORK/server.in" || true
    sleep 5
    # match only the server JVM, not this script (whose command line also mentions the jar)
    pkill -f "^java -Xmx2G -jar server.jar nogui" || true
    kill "$SERVER_PID" 2>/dev/null || true
}
trap cleanup EXIT

until grep -q "Done (" "$WORK/server.log" 2>/dev/null; do sleep 1; done
echo "server ready"

# Op the dev player once it joins, so the harness can change dimension with /execute.
( until grep -q "Dev joined the game" "$WORK/server.log" 2>/dev/null; do sleep 1; done; echo "op Dev" > "$WORK/server.in" ) &

MODS="$JAR${EXTRA_MODS:+:$EXTRA_MODS}"
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Dfabric.addMods=$MODS -Dconductor.harness.out=$OUT -Dconductor.harness.server=localhost:25565 -Dconductor.harness.scenario=${HARNESS_SCENARIO:-stage4} -Dconductor.harness.holdSeconds=${HARNESS_HOLD:-60}"
cd "$OSTINATO_DIR"
timeout "$CLIENT_TIMEOUT" xvfb-run -a -s "-screen 0 1280x720x24" \
    ./gradlew ${GRADLE_ARGS:-} :fabric:runClient -Pavailable_loaders=fabric --no-daemon \
    > "$WORK/client.log" 2>&1 || echo "client exited with $?"
echo "harness output: $OUT ($(wc -l < "$OUT" 2>/dev/null || echo 0) lines)"
