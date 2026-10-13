#!/usr/bin/env bash
# Production-style live run: a real Fabric Loader launch (KnotClient, game remapped to intermediary at start-up)
# with release jars in a mods/ folder, joining a local vanilla server. Unlike live-run.sh (Ostinato's dev client
# from source), this tests the jars exactly as a player installs them.
#
#   scripts/prod-run.sh <workdir>
#
# <workdir> must contain server/server.jar (vanilla, same Minecraft version) with eula.txt and server.properties.
# Env: MC (default 1.21.11), LOADER (Fabric Loader version, default 0.19.5),
#      MODS (colon-separated jars copied into the client's mods/ folder: Ostinato, Conductor, ...),
#      CONDUCTOR_MODULES, CONDUCTOR_CONFIG (as in live-run.sh), SERVER_ON_JOIN (as in live-run.sh),
#      SCREENSHOT_AT (seconds after joining; several, space-separated, give screenshot-<s>.png each),
#      HOLD (seconds to stay in the world, default 60),
#      SERVER_MODS (colon-separated jars: run the server as a Fabric dedicated server with these in its mods/ folder;
#      it needs <workdir>/server to have run once so the vanilla bundler unpacked versions/ and libraries/),
#      SERVER_MODULES (modules folder for that server, as CONDUCTOR_MODULES is for the client),
#      CLIENT_JVM_ARGS (extra JVM options for the client, e.g. -Dmixin.debug.export=true).
# The game, assets and libraries come from the local Gradle caches (scripts/prod_classpath.py).
# Output: <workdir>/prod-client.log, server.log, screenshot.png.
set -euo pipefail

WORK=$(realpath "$1")
ROOT=$(cd "$(dirname "$0")/.." && pwd)
MC=${MC:-1.21.11}
LOADER=${LOADER:-0.19.5}
HOLD=${HOLD:-60}
GAME="$WORK/prod-game"

python3 -I "$ROOT/scripts/prod_classpath.py" "$MC" "$LOADER" "$WORK/prod-classpath.txt" | tee "$WORK/prod-classpath.out"
ASSET_INDEX=$(grep -o 'assetIndex [0-9A-Za-z._-]*' "$WORK/prod-classpath.out" | cut -d' ' -f2)
CP=$(paste -sd: "$WORK/prod-classpath.txt")

rm -rf "$GAME" && mkdir -p "$GAME/mods"
IFS=':' read -ra JARS <<< "${MODS:-}"
for j in "${JARS[@]}"; do [ -n "$j" ] && cp "$j" "$GAME/mods/"; done
cat > "$GAME/options.txt" <<OPTS
onboardAccessibility:false
skipMultiplayerWarning:true
joinedFirstServer:true
tutorialStep:none
pauseOnLostFocus:false
soundCategory_master:0.0
OPTS

rm -f "$WORK/server.log" "$WORK/prod-client.log" "$WORK"/screenshot*.png
rm -rf "$WORK/server/world" "$WORK/server/mods"
mkfifo "$WORK/server.in" 2>/dev/null || true
if [ -n "${SERVER_MODS:-}" ]; then
    python3 -I "$ROOT/scripts/prod_classpath.py" "$MC" "$LOADER" "$WORK/prod-server-classpath.txt" "$WORK/server" > /dev/null
    SCP=$(paste -sd: "$WORK/prod-server-classpath.txt")
    mkdir -p "$WORK/server/mods"
    IFS=':' read -ra SJARS <<< "$SERVER_MODS"
    for j in "${SJARS[@]}"; do [ -n "$j" ] && cp "$j" "$WORK/server/mods/"; done
    SERVER_CMD=(java -Xmx2G ${SERVER_MODULES:+-Dconductor.modulesDir=$SERVER_MODULES} -cp "$SCP" net.fabricmc.loader.impl.launch.knot.KnotServer nogui)
else
    SERVER_CMD=(java -Xmx2G -jar server.jar nogui)
fi
(cd "$WORK/server" && tail -f "$WORK/server.in" | "${SERVER_CMD[@]}" > "$WORK/server.log" 2>&1) &
SERVER_PID=$!
cleanup() {
    echo "stop" > "$WORK/server.in" || true
    sleep 5
    pkill -f "^java -Xmx2G -jar server.jar nogui" || true
    pkill -f "KnotServer nogui" || true
    pkill -f "tail -f $WORK/server.in" || true
    kill "$SERVER_PID" 2>/dev/null || true
    # helpers still waiting for a join that never came must not fire in the next run
    kill $(jobs -p) 2>/dev/null || true
}
trap cleanup EXIT
until grep -q "Done (" "$WORK/server.log" 2>/dev/null; do
    if ! kill -0 "$SERVER_PID" 2>/dev/null || grep -qE 'Exception in thread "main"|---- Minecraft Crash Report ----|Failed to start the minecraft server|Incompatible mods found' "$WORK/server.log" 2>/dev/null; then
        echo "server failed before it was ready"; tail -30 "$WORK/server.log"; exit 1
    fi
    sleep 1
done
echo "server ready"

(
    until grep -q "Dev joined the game" "$WORK/server.log" 2>/dev/null; do sleep 1; done
    echo "op Dev" > "$WORK/server.in"
    IFS=';' read -ra CMDS <<< "${SERVER_ON_JOIN:-}"
    for cmd in "${CMDS[@]}"; do
        cmd="${cmd#"${cmd%%[![:space:]]*}"}"
        case "$cmd" in
            "") ;;
            sleep\ *) sleep "${cmd#sleep }" ;;
            *) echo "$cmd" > "$WORK/server.in" ;;
        esac
    done
) &
if [ -n "${SCREENSHOT_AT:-}" ]; then
    (
        until grep -q "Dev joined the game" "$WORK/server.log" 2>/dev/null; do sleep 1; done
        waited=0
        for at in $SCREENSHOT_AT; do
            sleep $((at - waited)); waited=$at
            XVFB=$(pgrep -a Xvfb | head -1)
            DISPLAY_NUM=$(echo "$XVFB" | grep -o ' :[0-9]*' | head -1 | tr -d ' ')
            AUTH=$(echo "$XVFB" | grep -o -- '-auth [^ ]*' | cut -d' ' -f2)
            XAUTHORITY="$AUTH" import -window root -display "$DISPLAY_NUM" "$WORK/screenshot-$at.png" && echo "screenshot saved: $WORK/screenshot-$at.png"
            [ -f "$WORK/screenshot.png" ] || cp "$WORK/screenshot-$at.png" "$WORK/screenshot.png"
        done
    ) &
fi
# stop the client HOLD seconds after it joined
( until grep -q "Dev joined the game" "$WORK/server.log" 2>/dev/null; do sleep 1; done; sleep "$HOLD"; pkill -f "KnotClient" || true ) &

JVM_PROPS="${CONDUCTOR_MODULES:+-Dconductor.modulesDir=$CONDUCTOR_MODULES} ${CONDUCTOR_CONFIG:+-Dconductor.configDir=$CONDUCTOR_CONFIG} ${CLIENT_JVM_ARGS:-}"
# quick play arrived in 1.20. Before it, --server connects during the first resource reload, and vanilla 1.16.1 then
# crashes drawing chunks before block models are baked (seen with no mods at all). Older versions join through
# scripts/legacy-join, a harness-only mod that connects once loading is over.
case "$MC" in
    1.1[0-9]|1.1[0-9].*)
        JOIN=""
        JOINDIR="$WORK/legacy-join"; rm -rf "$JOINDIR"; mkdir -p "$JOINDIR/classes"
        javac --release 8 -nowarn -d "$JOINDIR/classes" -cp "$(head -1 "$WORK/prod-classpath.txt")" \
            "$ROOT/scripts/legacy-join/src/conductor/testjoin/LegacyJoin.java"
        cp "$ROOT/scripts/legacy-join/fabric.mod.json" "$JOINDIR/classes/"
        (cd "$JOINDIR/classes" && jar cf "$GAME/mods/conductor-test-join.jar" .)
        JVM_PROPS="$JVM_PROPS -Dconductor.testJoin=localhost:25565" ;;
    *) JOIN="--quickPlayMultiplayer localhost:25565" ;;
esac
timeout "${CLIENT_TIMEOUT:-900}" xvfb-run -a -s "-screen 0 1280x720x24" \
    java -Xmx3G $JVM_PROPS -cp "$CP" net.fabricmc.loader.impl.launch.knot.KnotClient \
    --username Dev --version "$MC" --gameDir "$GAME" --assetsDir /root/.gradle/caches/unimined/assets \
    --assetIndex "$ASSET_INDEX" --uuid 00000000-0000-0000-0000-0000000000d1 --accessToken 0 --userType legacy \
    --versionType release $JOIN \
    > "$WORK/prod-client.log" 2>&1 || echo "client exited with $?"
echo "done; client log: $WORK/prod-client.log"
